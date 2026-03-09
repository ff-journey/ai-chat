from typing import Annotated
from dataclasses import dataclass
from langchain_core.messages import AnyMessage
from langchain_core.runnables import RunnableConfig
from langgraph.graph import MessagesState


class WorkerState(MessagesState):
    next: str          # Supervisor 决定下一个节点
    current_agent: str # 当前正在执行的 agent

@dataclass
class WorkConfig:
    model_name: str
    api_key: str
    base_url: str
    user_id: str
