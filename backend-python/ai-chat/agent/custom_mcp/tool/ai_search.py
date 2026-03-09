import os

import requests
from dotenv import load_dotenv
from langchain_core.tools import tool

file_a_path = os.path.abspath(__file__)
file_a_dir = os.path.dirname(file_a_path)
target_path = os.path.join(file_a_dir, "../.env")
load_dotenv(dotenv_path=target_path)
DASHSCOPE_API_KEY = os.getenv("DASHSCOPE_API_KEY")
AMAP_API_KEY = os.getenv("AMAP_API_KEY")
ALI_SEARCH_API_KEY = os.getenv("ALI_SEARCH_API_KEY")

@tool
async def ai_search(content):
    """网络搜索"""
    url = f"http://default-21yp.platform-cn-shanghai.opensearch.aliyuncs.com/v3/openapi/workspaces/default/web-search/ops-web-search-001"
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {ALI_SEARCH_API_KEY}"
    }
    data = {
        "query": content,
        "hit": 10,
        "sort": [
            {
                "field": "score",
                "order": "desc"
            }
        ]
    }
    response = requests.post(url, headers=headers, json=data)
    return response.json()['result']['search_result']