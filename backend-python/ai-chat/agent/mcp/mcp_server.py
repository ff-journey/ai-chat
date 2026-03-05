from fastmcp import FastMCP, Context
from fastmcp.dependencies import CurrentContext
import os
import requests
from dotenv import load_dotenv
from agent.tool.ai_search import ai_search

mcp = FastMCP()


@mcp.tool()
async def ai_search_mcp(content):
    return await ai_search(content)

def mcp_main():
    mcp.run(transport="stdio")