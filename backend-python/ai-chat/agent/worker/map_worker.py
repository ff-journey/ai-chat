from typing import Annotated

import httpx
from langchain_core.messages import SystemMessage
from langchain_core.runnables import RunnableConfig
from langgraph.config import get_config
from langgraph.prebuilt import ToolNode, InjectedState
from langgraph.graph import StateGraph, MessagesState, START, END
from ..agent_state import WorkerState, WorkConfig
from langchain.tools import tool
import os
from dotenv import load_dotenv
from ..llm_config import get_llm
from ..custom_mcp.tool.ai_search import ai_search

file_a_path = os.path.abspath(__file__)
file_a_dir = os.path.dirname(file_a_path)
target_path = os.path.join(file_a_dir, "../.env")
load_dotenv(dotenv_path=target_path)
DASHSCOPE_API_KEY = os.getenv("DASHSCOPE_API_KEY")
AMAP_API_KEY = os.getenv("AMAP_API_KEY")
ALI_SEARCH_API_KEY = os.getenv("ALI_SEARCH_API_KEY")

@tool
async def _weather_tool(city: str) -> str:
    """查询城市详细的天气, 以及未来三天的天气预报"""
    url = f"https://restapi.amap.com/v3/weather/weatherInfo?city={city}&key={AMAP_API_KEY}&extensions=all"
    async with httpx.AsyncClient() as client:
        response = await client.get(url)
    data = response.json()
    if data["status"] == "1":
        return data["forecasts"]
    else:
        return f"查询失败,{data['info']}"

tools = [_weather_tool,ai_search]

async def _map_worker(state: WorkerState, config: RunnableConfig):
    prompt = """你是出行建议专家，使用工具获取未来3天的天气预报, 给出出行和穿衣建议, 必要时提供路况信息。

    工作规则：
    1. 如果消息中还没有天气数据，调用 _weather_tool 工具获取
    2. 如果消息中还没有目的地路况信息，调用 ai_search 工具搜索
    3. 如果消息中已经有工具返回的天气数据和目的地路况信息，直接分析并给出结论，不要再调用工具
    4. 回答需简洁明确, 主次分明

    请先检查对话历史中是否已有天气数据和目的地路况信息。"""
    configurable = config.get("configurable", {})
    clean_messages = [
        msg for msg in state["messages"]
        if not isinstance(msg, SystemMessage)
    ]
    messages = [SystemMessage(content=prompt)] + clean_messages
    work_config = WorkConfig(
        model_name=configurable.get("model_name"),
        api_key=configurable.get("api_key"),
        base_url=configurable.get("base_url"),
        user_id=configurable.get("user_id"),
    )
    llm = get_llm(work_config.model_name, work_config.api_key, work_config.base_url)
    response = llm.bind_tools(tools).invoke(messages,config)
    return WorkerState(messages=[response], next="", current_agent="map_worker")

def _should_continue(state: WorkerState):
    last_msg = state['messages'][-1]
    if hasattr(last_msg, "tool_calls") and last_msg.tool_calls:
        return "tools"
    return END

def _compile_workflow():
    workflow = StateGraph(WorkerState)
    workflow.add_node("agent", _map_worker)
    workflow.add_node("tools", ToolNode(tools))
    workflow.add_edge(START, "agent")
    workflow.add_conditional_edges("agent", _should_continue, {"tools": "tools", END: END})
    app = workflow.compile()
    return app

app = _compile_workflow()

def get_weather_tour_advice_app():
    return app

@tool
async def get_weather_tour_advice(state: Annotated[MessagesState, InjectedState],config: RunnableConfig) -> str:
    """根据城市名获取天气预报和路况信息"""
    state = WorkerState(messages=state["messages"], next="", current_agent="map_worker")
    result = await app.invoke(state, config)
    messages_out = result.get("messages", [])
    if messages_out:
        return messages_out[-1].content
    return "无法获取信息，请稍后重试"