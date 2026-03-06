import asyncio

import streamlit as st
from langchain_openai.chat_models import ChatOpenAI
from langchain_core.callbacks.base import BaseCallbackHandler
from langchain_core.messages import ChatMessage, SystemMessage, AIMessage, HumanMessage
# from ...agent.agent_main import get_main_app
from agent.agent_main import get_main_app



st.title("哈基米聊天室")

# 自定义CSS：仅修改用户消息的布局
st.markdown("""
<style>
/* 选中用户消息容器 */
[data-testid="stChatMessage"]:has([data-testid="stChatMessageAvatarUser"]) {
    /* 容器整体靠右，最大宽度55%，宽度随内容动态变化 */
    max-width: 55% !important;
    width: fit-content !important;
    margin-left: auto !important;
    margin-right: 0 !important;
    
    /* 关键：flex 布局反向，头像在右，文字块在左 */
    flex-direction: row-reverse !important;
    justify-content: flex-end !important;
    align-items: flex-start !important;
}

/* 选中用户消息的文字内容块 */
[data-testid="stChatMessage"]:has([data-testid="stChatMessageAvatarUser"]) [data-testid="stChatMessageContent"] {
    /* 文字块内部左对齐，解决最后一行问题 */
    text-align: left !important;
    /* 给文字块和头像之间留一点间距 */
    margin-right: 0.8rem !important;
    margin-left: 0 !important;
}

/* 确保头像在最右侧 */
[data-testid="stChatMessage"]:has([data-testid="stChatMessageAvatarUser"]) [data-testid="stChatMessageAvatarUser"] {
    margin-left: 0 !important;
    margin-right: 0 !important;
}
</style>
""", unsafe_allow_html=True)



openai_api_key = st.sidebar.text_input("OpenAI API Key", type="password", value="")
base_url = st.sidebar.text_input("OpenAI API Url",value="https://dashscope.aliyuncs.com/compatible-mode/v1")


def generate_response(input_text):
    model = ChatOpenAI(temperature=0.7, api_key="", model="qwen-turbo", base_url="https://dashscope.aliyuncs.com/compatible-mode/v1")
    st.info(model.invoke(input_text))


if "app" not in st.session_state:
    st.session_state.app = get_main_app()



import streamlit as st

if "messages" not in st.session_state:
    st.session_state["messages"] = [AIMessage(role="assistant", content="害嗨嗨 !")]
for msg in st.session_state.messages:
    with st.chat_message(msg.role):
        st.markdown(msg.content)

#
# class StreamHandler(BaseCallbackHandler):
#     def __init__(self, container, initial_text=""):
#         self.container = container
#         self.text = initial_text
#
#     def on_llm_new_token(self, token: str, **kwargs) -> None:
#         self.text += token
#         self.container.markdown(self.text)
#
#
# if prompt := st.chat_input("Say something"):
#     prompt_ = HumanMessage(role="user",content=prompt)
#     with st.chat_message("user"):
#         st.markdown(prompt)
#     st.session_state.messages.append(prompt_)
#     with st.chat_message("assistant"):
#         stream_handler = StreamHandler(st.empty())
#         app = get_main_app()
#
#
#
#         app.stream()
#         response = llm.invoke(st.session_state.messages)
#         st.session_state.messages.append(AIMessage(role="assistant",content=response.content))


async def run_stream(app, input_state, config):
    full_response = ""
    # stream_mode="messages" 会在每个 token 生成时触发事件
    async for event in app.astream_events(input_state, config=config, version="v2"):
        kind = event["event"]
        # 只关心 LLM 产出 token 的事件
        if kind == "on_chat_model_stream":
            chunk = event["data"]["chunk"]
            if chunk.content:
                full_response += chunk.content
                yield chunk.content  # 逐步产出内容


# 在 Streamlit 里使用
if prompt := st.chat_input("请输入你的问题"):
    prompt_ = HumanMessage(role="user",content=prompt)
    with st.chat_message("user"):
        st.markdown(prompt)
    st.session_state.messages.append(prompt_)
    with st.chat_message("assistant"):
        # write_stream 会自动处理生成器，实现打字机效果
        response_placeholder = st.empty()
        collected = [""]
        config = {
            "configurable": {
                "thread_id": "1",
                "model_name": "qwen-turbo",
                "api_key": openai_api_key,
                "base_url": base_url,
            }
        }
        async def collect_stream():
            async for event in st.session_state.app.astream_events(
                    {"messages": prompt_},
                    config=config,
                    version="v2"
            ):
                if event["event"] == "on_chat_model_stream":
                    chunk = event["data"]["chunk"]
                    if chunk.content:
                        collected[0] += chunk.content
                        response_placeholder.markdown(collected[0] + "▌")
            response_placeholder.markdown(collected[0])

        asyncio.run(collect_stream())
        st.session_state.messages.append(
            AIMessage(role="assistant", content=collected[0])
            )