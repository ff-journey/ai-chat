# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Structure

This repository contains two main backends for an AI chat application:

### Backend-Java (`backend-java/ai-chat-ali`)
- **Framework**: Spring Boot 3.5.11 with Java 21
- **AI Integration**: Spring AI Alibaba (Dashscope integration)
- **Purpose**: Alibaba Cloud AI service integration
- **Build Tool**: Gradle
- **Main Class**: `AiChatAliApplication.java`

### Backend-Python (`backend-python/ai-chat`)
- **Framework**: LangChain with LangGraph for agent orchestration
- **UI**: Streamlit web interface
- **Purpose**: Multi-agent AI chat system with weather/tourism capabilities
- **Python**: ≥3.11 with uv package manager
- **Main Entry**: `main.py` (launches Streamlit app)

## Architecture Overview

### Python Backend Architecture
The Python backend implements a multi-agent system using LangGraph:

1. **Agent Supervisor**: Routes requests between specialized workers
   - Routes to `map_worker` for weather/tourism queries
   - Routes to `chat` for general conversation
   - Decides when to finish the conversation

2. **Workers**:
   - `map_worker`: Provides weather forecasts and tour advice using Amap API
   - `chat`: General conversation with AI search capabilities

3. **Tools**:
   - `ai_search`: Web search functionality
   - Custom MCP (Model Context Protocol) server integration

4. **LLM Configuration**:
   - Uses OpenAI-compatible API through `ChatOpenAI`
   - Supports custom base URLs and API keys
   - LangSmith tracing enabled for debugging

### Key Dependencies
- **LangChain**: Core framework for LLM integration
- **LangGraph**: Agent orchestration and workflow management
- **FastMCP**: Model Context Protocol server implementation
- **Streamlit**: Web UI for the chat interface
- **Spring AI Alibaba**: Java backend for Alibaba Cloud services

## Development Commands

### Python Backend
```bash
# Install dependencies
uv sync

# Start Streamlit app
cd backend-python/ai-chat
python main.py

# Run specific Streamlit app
streamlit run streamlit-ex/streamlit/chat_ui.py

# Run basic streaming demo
streamlit run streamlit-ex/streamlit/basic_streaming.py
```

### Java Backend
```bash
# Build and run
cd backend-java/ai-chat-ali
./gradlew bootRun

# Run tests
./gradlew test

# Build JAR
./gradlew build
```

## Environment Configuration

The Python backend requires several API keys in `.env`:
- `DASHSCOPE_API_KEY`: Alibaba Dashscope API
- `AMAP_API_KEY`: Amap (AutoNavi) API for weather/location
- `ALI_SEARCH_API_KEY`: Alibaba search API
- `LANGSMITH_API_KEY`: LangSmith tracing API

## File Structure Notes

- `backend-python/ai-chat/agent/`: Core agent implementation
- `backend-python/ai-chat/agent/custom_mcp/`: MCP server and tools
- `backend-python/ai-chat/streamlit-ex/`: Streamlit UI applications
- `backend-java/ai-chat-ali/`: Spring Boot application with Alibaba AI integration

## Important Implementation Details

1. **Memory Management**: Uses LangGraph's MemorySaver for conversation persistence
2. **Tool Routing**: Smart routing between chat and specialized workers
3. **UTF-8 Encoding**: Explicitly set in Java backend for international support
4. **Streaming**: LLM responses are streamed for better UX
5. **Multi-model Support**: Configurable LLM endpoints through environment variables