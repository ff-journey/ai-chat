import os
from typing import TypedDict, Annotated

from langchain_core.runnables import RunnableConfig
from langchain_core.tools import tool
from langgraph.checkpoint.memory import MemorySaver

from .custom_mcp.tool.ai_search import ai_search
from langchain_core.messages import SystemMessage
from langgraph.constants import START, END
from langgraph.graph import MessagesState, StateGraph
from langgraph.prebuilt import ToolNode, InjectedState
from .llm_config import get_llm
from .agent_state import WorkerState
from .worker.map_worker import get_weather_tour_advice_app

WORKERS = ["map_worker"]
os.environ["LANGCHAIN_TRACING_V2"] = "true"  # 总开关，决定启用追踪功能
os.environ["LANGCHAIN_PROJECT"] = "ai_chat_test"  # 自定义项目名
os.environ["LANGSMITH_ENDPOINT"] = "https://api.smith.langchain.com"
os.environ["LANGSMITH_API_KEY"] = ""
def route_supervisor(state: WorkerState):
    if state['next'] == "FINISH":
        return END
    return state['next']

def should_continue(state: WorkerState):
    last_msg = state['messages'][-1]
    if hasattr(last_msg, "tool_calls") and last_msg.tool_calls:
        return "tools"
    return "supervisor"

def route_after_tool(state:WorkerState):
    # 工具执行完后，通过next_speaker知道是谁调用的，路由回去
    return state["next"]

def chat_node(state: WorkerState, config: RunnableConfig):
    """普通对话节点，直接用 LLM 回答，不调用任何工具"""
    configurable = config.get("configurable", {})

    # 过滤掉 SystemMessage，避免历史的 supervisor prompt 污染对话
    clean_messages = [
        msg for msg in state["messages"]
        if not isinstance(msg, SystemMessage)
    ]

    llm = get_llm(
        configurable.get("model_name"),
        configurable.get("api_key"),
        configurable.get("base_url")
    )
    response = llm.invoke(clean_messages)

    # 把回复追加到 messages，下次 supervisor 看到它就会判断 FINISH
    return {"messages": [response]}

def supervisor(state: WorkerState, config: RunnableConfig):
    supervisor_prompt = f"""你是一个任务分发助手，根据用户的问题决定交给哪个 Agent 处理。
    可选的 Agent：
    - map_worker：获得当前以及未来3天的详细天气预报和路况信息 。
    - ai_search：网络搜索
    - FINISH：任务已完成，直接回复用户
    
    只返回一个 Agent 名称，不要有任何其他内容。
    """
    configurable = config.get("configurable", {})
    clean_messages = [
        msg for msg in state["messages"]
        if not isinstance(msg, SystemMessage)
    ]
    messages = [SystemMessage(content=supervisor_prompt)] + clean_messages
    print(messages)
    llm = get_llm(configurable.get("model_name"), configurable.get("api_key"), configurable.get("base_url"))
    response = llm.invoke(messages)
    next_agent = response.content.strip()
    # 防止 LLM 返回非法值
    if next_agent not in WORKERS:
        next_agent = "FINISH"
    return {"next": next_agent}

tool_node = ToolNode([ai_search])
map_worker = get_weather_tour_advice_app

workflow = StateGraph(WorkerState)

workflow.add_node("supervisor", supervisor)
workflow.add_node("map_worker", map_worker)
workflow.add_node("tools", tool_node)

workflow.add_edge(START, "supervisor")
workflow.add_conditional_edges("supervisor", route_supervisor)
for next in WORKERS:
    workflow.add_conditional_edges(next, should_continue, {"tools":"tools", "supervisor":"supervisor"})

workflow.add_conditional_edges("tools", route_after_tool)
app = workflow.compile(
    checkpointer=MemorySaver(),
    # interrupt_before=["tools"]
)
def get_main_app():

    return app

# @tool
# async def supervisor_router(state: Annotated[MessagesState, InjectedState],config: RunnableConfig):
#     """
#     提供天气, 路况, 出行建议
#     """
#     state = WorkerState(messages=state["messages"], next="", current_agent="supervisor_router")
#     result = await app.invoke(state, config)
#     messages_out = result.get("messages", [])
#     if messages_out:
#         return {"result":messages_out[-1].content}
#     return {"result":"无法获取信息，请稍后重试"}
