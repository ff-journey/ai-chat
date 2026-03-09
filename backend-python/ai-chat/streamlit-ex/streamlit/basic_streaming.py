import sys
import time
from pathlib import Path
import subprocess

from langchain_core.callbacks.base import BaseCallbackHandler
from langchain_core.messages import ChatMessage, SystemMessage, AIMessage, HumanMessage
from langchain_openai import ChatOpenAI
import streamlit as st
from mcp.server.fastmcp.prompts.base import UserMessage


class StreamHandler(BaseCallbackHandler):
    def __init__(self, container, initial_text=""):
        self.container = container
        self.text = initial_text

    def on_llm_new_token(self, token: str, **kwargs) -> None:
        self.text += token
        self.container.markdown(self.text)


with st.sidebar:
    openai_api_key = st.text_input("OpenAI API Key", type="password")
    base_url = st.text_input("OpenAI API url")

if "messages" not in st.session_state:
    st.session_state["messages"] = [SystemMessage(role="assistant", content="How can I help you?")]

for msg in st.session_state.messages:
    st.chat_message(msg.role).write(msg.content)

if prompt := st.chat_input():
    st.session_state.messages.append(HumanMessage(role="user", content=prompt))
    st.chat_message("user").write(prompt)

    if not openai_api_key:
        st.info("Please add your OpenAI API key to continue.")
        st.stop()

    with st.chat_message("assistant"):
        stream_handler = StreamHandler(st.empty())
        llm = ChatOpenAI(model="qwen-turbo", openai_api_key=openai_api_key, base_url=base_url, streaming=True, callbacks=[stream_handler])
        response = llm.invoke(st.session_state.messages)
        st.session_state.messages.append(AIMessage(role="assistant", content=response.content))


if __name__ == '__main__':
    script_dir = Path(__file__).parent
    app_path = script_dir/"basic_streaming.py"
    process = subprocess.run(
        [sys.executable, "-m", "streamlit", "run", str(app_path), "--server.port", str(8501), "--server.headless", "true", '--server.enableCORS', 'false', '--server.enableXsrfProtection', 'false', '--server.address', '0.0.0.0'],
    )
    print(process.returncode)
    print(process.stdout)