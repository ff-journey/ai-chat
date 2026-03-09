from langchain_core.callbacks import Callbacks
from langchain_openai.chat_models import ChatOpenAI
from pydantic import Field
from functools import lru_cache



@lru_cache(maxsize=128)
def get_llm(model, openai_api_key, base_url):
    return ChatOpenAI(model=model, api_key=openai_api_key, base_url=base_url, streaming=True)
