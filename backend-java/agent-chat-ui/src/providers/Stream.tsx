import React, {
  createContext,
  useContext,
  ReactNode,
  useState,
  useEffect,
  useCallback,
  useRef,
  useMemo,
} from "react";
import { useQueryState } from "nuqs";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useThreads } from "./Thread";
import { toast } from "sonner";
import { createApiClient, ToolFeedbackDTO } from "@/lib/spring-ai-api";
import { UIMessage, createUIMessage, fromMessageDTO } from "@/types/messages";

export interface ContentBlock {
  type: string;
  mime_type?: string;
  data?: string;
  [key: string]: any;
}

interface StreamContextType {
  messages: UIMessage[];
  isStreaming: boolean;
  sendMessage: (content: string, contentBlocks?: ContentBlock[]) => Promise<void>;
  resumeFeedback: (toolFeedbacks: ToolFeedbackDTO[]) => Promise<void>;
  clearMessages: () => void;
}

const StreamContext = createContext<StreamContextType | undefined>(undefined);

export const useStream = () => {
  const context = useContext(StreamContext);
  const { currentThreadId } = useThreads();

  if (!context) {
    throw new Error("useStream must be used within a StreamProvider");
  }

  const { clearMessages } = context;

  useEffect(() => {
    if (currentThreadId === null) {
      clearMessages();
    }
  }, [currentThreadId, clearMessages]);

  return context;
};

// Alias for backward compatibility
export const useStreamContext = useStream;

interface StreamProviderProps {
  children: ReactNode;
}

export const StreamProvider: React.FC<StreamProviderProps> = ({ children }) => {
  const [messages, setMessages] = useState<UIMessage[]>([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const [isLoadingMessages, setIsLoadingMessages] = useState(false);
  const { currentThreadId, createThread, appName, userId, mode, selectedGraph, updateThreadSummary } = useThreads();
  const abortControllerRef = useRef<AbortController | null>(null);
  const skipNextLoadRef = useRef(false);
  const messagesRef = useRef<UIMessage[]>([]);

  // Keep messagesRef in sync with messages state
  useEffect(() => {
    messagesRef.current = messages;
  }, [messages]);

  const loadThreadMessages = async (threadId: string) => {
    if (mode === 'agent' && !appName) return;
    if (mode === 'graph' && !selectedGraph) return;
    setIsLoadingMessages(true);
    try {
      console.log('[Stream] Loading thread messages for threadId:', threadId);

      const apiClient = createApiClient();
      const session = mode === 'graph'
        ? await apiClient.getGraphSession(selectedGraph, userId, threadId)
        : await apiClient.getSession(appName, userId, threadId);

      console.log('[Stream] Loaded session:', session);

      // Extract messages from session.values.messages (Studio protocol)
      if (session.values && Array.isArray(session.values.messages) && session.values.messages.length > 0) {
        const loadedMessages = session.values.messages.map((msg, index) =>
          createUIMessage(fromMessageDTO(msg), `${threadId}-${index}`)
        );
        setMessages(loadedMessages);
        console.log('[Stream] Loaded messages from session:', loadedMessages);
      } else {
        // Fallback: custom history endpoint (reads from MemorySaver / ChatHistoryService)
        console.log('[Stream] Session values empty, trying custom history endpoint...');
        const historyMsgs = await apiClient.getThreadHistory(threadId, appName);
        if (historyMsgs.length > 0) {
          const loadedMessages = historyMsgs.map((msg, index) =>
            createUIMessage(fromMessageDTO(msg), `${threadId}-${index}`)
          );
          setMessages(loadedMessages);
          console.log('[Stream] Loaded messages from history endpoint:', loadedMessages);
        } else {
          // No messages found - empty thread or newly created
          setMessages([]);
          console.log('[Stream] No messages found for thread, showing empty state');
        }
      }
    } catch (error) {
      console.error("Failed to load thread messages:", error);
      toast.error("Failed to load thread messages");
      setMessages([]);
    } finally {
      setIsLoadingMessages(false);
    }
  };

  // Load messages when thread or selected agent changes
  useEffect(() => {
    if (currentThreadId && appName) {
      // Skip loading if we just auto-created a thread during sendMessage
      if (skipNextLoadRef.current) {
        skipNextLoadRef.current = false;
        return;
      }
      loadThreadMessages(currentThreadId);
    } else if (!currentThreadId) {
      setMessages([]);
    }
  }, [currentThreadId, appName, mode, selectedGraph, userId]);

  const sendMessage = useCallback(
    async (content: string, contentBlocks?: ContentBlock[]) => {
      if (!content.trim()) {
        return;
      }
      if (mode === 'agent' && !appName) {
        toast.error("No agent selected. Please select an agent from the list.");
        return;
      }
      if (mode === 'graph' && !selectedGraph) {
        toast.error("No graph selected. Please select a graph from the list.");
        return;
      }

      // Capture user message count before sending (for summary trigger)
      const prevUserMsgCount = messagesRef.current.filter(
        (m) => m.message.messageType === 'user'
      ).length;
      const newUserMsgCount = prevUserMsgCount + 1;

      // Auto-create thread if none exists
      let activeThreadId = currentThreadId;
      if (!activeThreadId) {
        toast.info("Creating new thread...");
        // Set flag to prevent the useEffect from wiping messages when currentThreadId changes
        skipNextLoadRef.current = true;
        const newThread = await createThread();
        if (!newThread) {
          skipNextLoadRef.current = false;
          toast.error("Failed to create thread");
          return;
        }
        activeThreadId = newThread.thread_id;
      }

      // Add user message immediately - create proper UIMessage structure
      // Collect image previews from content blocks so they render in the chat bubble
      const imageBlocks = (contentBlocks || []).filter((b) => b.type === 'image');
      const mediaForMessage = imageBlocks.map((block) => ({
        mimeType: block.mime_type || 'image/jpeg',
        // Both uploaded images (createObjectURL) and sample images have _previewUrl set
        data: block.metadata?._previewUrl
          ? String(block.metadata._previewUrl)
          : String(block.data || ''),
      }));

      const userUIMessage: UIMessage = {
        id: `user-${Date.now()}`,
        message: {
          messageType: 'user',
          content: content.trim(),
          metadata: {},
          media: mediaForMessage,
        },
        timestamp: Date.now()
      };

      setMessages((prev) => [...prev, userUIMessage]);
      setIsStreaming(true);

      // Create new abort controller for this request
      abortControllerRef.current = new AbortController();

      try {
        const apiClient = createApiClient();

        // Start streaming - convert Message to UserMessage (with messageType for backend)
        // Extract original File from content blocks for multimodal upload
        const imageBlock = (contentBlocks || []).find(
          (block) => block.type === 'image' && block.metadata?._originalFile
        );
        const uploadFile: File | undefined = imageBlock?.metadata?._originalFile as File | undefined;
        // Also check for sample image selection
        const sampleId: string | undefined = (contentBlocks || []).find(
          (block) => block.type === 'image' && block.metadata?._sampleId
        )?.metadata?._sampleId as string | undefined;

        // Route: multimodal (has image file or sample) → /api/chat/multimodal
        //        normal text        → /run_sse (Studio protocol)
        if (uploadFile || sampleId) {
          console.log('[Stream] Using multimodal endpoint for image upload');

          const multimodalStream = sampleId
            ? apiClient.runSampleImageStream(
                content.trim(),
                activeThreadId,
                sampleId,
                abortControllerRef.current.signal
              )
            : apiClient.runMultimodalStream(
                content.trim(),
                activeThreadId,
                uploadFile!,
                abortControllerRef.current.signal
              );

          let isFirstChunk = true;
          for await (const chunk of multimodalStream) {
            if (isFirstChunk) {
              const newAssistantMessage: UIMessage = {
                id: `assistant-${Date.now()}`,
                message: {
                  messageType: 'assistant',
                  content: chunk,
                  metadata: {},
                },
                timestamp: Date.now()
              };
              setMessages((prev) => [...prev, newAssistantMessage]);
              isFirstChunk = false;
            } else {
              setMessages((prev) => {
                const newMessages = [...prev];
                const last = newMessages[newMessages.length - 1];
                newMessages[newMessages.length - 1] = {
                  ...last,
                  message: {
                    ...last.message,
                    content: last.message.content + chunk
                  }
                };
                return newMessages;
              });
            }
          }
        } else {
          // Standard Studio protocol path (/run_sse)
          const userMessageForApi: import("@/lib/spring-ai-api").UserMessage = {
            messageType: "user",
            content: content.trim(),
            metadata: {},
            media: []
          };

          const stream = mode === 'graph'
            ? apiClient.runGraphStream(
                selectedGraph,
                userId,
                activeThreadId,
                userMessageForApi,
                abortControllerRef.current.signal
              )
            : apiClient.runAgentStream(
                appName,
                userId,
                activeThreadId,
                userMessageForApi,
                abortControllerRef.current.signal
              );

          let isFirstChunk = true;
          console.log('[Stream] Starting to process agent responses...');

          for await (const agentResponse of stream) {
            console.log('[Stream] Received agent response:', agentResponse);

            if (agentResponse.node === "heartbeat") continue;

            // Check message first: when the Studio sends both chunk and message in the
            // same final event, we treat the complete message as authoritative and ignore
            // the chunk to prevent the content from being appended a second time.
            if (agentResponse.message) {
              const backendMessage = fromMessageDTO(agentResponse.message);
              const messageType = agentResponse.message.messageType;

              if (messageType === 'assistant' || messageType === 'tool-request') {
                if (isFirstChunk) {
                  const newMessage: UIMessage = {
                    id: `${messageType}-${Date.now()}`,
                    message: backendMessage,
                    timestamp: Date.now()
                  };
                  setMessages((prev) => [...prev, newMessage]);
                  isFirstChunk = false;
                } else {
                  setMessages((prev) => {
                    const newMessages = [...prev];
                    const lastMessage = newMessages[newMessages.length - 1];
                    // message is authoritative — replace accumulated streaming content entirely
                    newMessages[newMessages.length - 1] = {
                      ...lastMessage,
                      message: {
                        ...backendMessage,
                        content: backendMessage.content || lastMessage.message.content
                      }
                    };
                    return newMessages;
                  });
                }
              } else if (messageType === 'tool-confirm' || messageType === 'tool') {
                const newMessage: UIMessage = {
                  id: `${messageType}-${Date.now()}`,
                  message: backendMessage,
                  timestamp: Date.now()
                };
                setMessages((prev) => [...prev, newMessage]);
                isFirstChunk = true;
              }
            } else if (agentResponse.chunk) {
              if (isFirstChunk) {
                const newAssistantMessage: UIMessage = {
                  id: `assistant-${Date.now()}`,
                  message: {
                    messageType: 'assistant',
                    content: agentResponse.chunk,
                    metadata: {},
                    toolCalls: []
                  },
                  timestamp: Date.now()
                };
                setMessages((prev) => [...prev, newAssistantMessage]);
                isFirstChunk = false;
              } else {
                setMessages((prev) => {
                  const newMessages = [...prev];
                  const lastMessage = newMessages[newMessages.length - 1];
                  newMessages[newMessages.length - 1] = {
                    ...lastMessage,
                    message: {
                      ...lastMessage.message,
                      content: lastMessage.message.content + agentResponse.chunk
                    }
                  };
                  return newMessages;
                });
              }
            }
          }
        }

        console.log('[Stream] Streaming complete');

        // Trigger LLM summary every 5 user turns
        if (newUserMsgCount % 5 === 0 && activeThreadId) {
          const threadForSummary = activeThreadId;
          setTimeout(async () => {
            try {
              const latestMessages = messagesRef.current;
              const summaryMessages = latestMessages
                .filter((m) => m.message.messageType === 'user' || m.message.messageType === 'assistant')
                .map((m) => ({ role: m.message.messageType as string, content: m.message.content }));
              if (summaryMessages.length > 0) {
                const apiClient = createApiClient();
                const summary = await apiClient.generateSummary(summaryMessages);
                updateThreadSummary(threadForSummary, summary);
              }
            } catch (e) {
              console.warn('[Stream] Summary generation failed:', e);
            }
          }, 300);
        }

      } catch (error: any) {
        if (error.name === "AbortError") {
          console.log("Request was aborted");
          toast.info("Request cancelled");
        } else {
          console.error("Failed to send message:", error);
          toast.error(error.message || "Failed to send message");
        }
      } finally {
        setIsStreaming(false);
        abortControllerRef.current = null;
      }
    },
    [currentThreadId, createThread, appName, userId, mode, selectedGraph, updateThreadSummary]
  );

  const clearMessages = useCallback(() => {
    setMessages([]);
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
  }, []);

  const resumeFeedback = useCallback(
    async (toolFeedbacks: ToolFeedbackDTO[]) => {
      if (!currentThreadId) {
        toast.error("No active thread");
        return;
      }
      if (mode === 'agent' && !appName) {
        toast.error("No agent selected.");
        return;
      }
      if (mode === 'graph' && !selectedGraph) {
        toast.error("No graph selected.");
        return;
      }

      if (mode === 'graph') {
        toast.info("Resume with tool feedback is not yet supported for graphs.");
        return;
      }

      setIsStreaming(true);

      abortControllerRef.current = new AbortController();

      try {
        const apiClient = createApiClient();

        const stream = apiClient.resumeAgentStream(
              appName,
              userId,
              currentThreadId,
              toolFeedbacks,
              abortControllerRef.current.signal
            );

        let isFirstChunk = true;
        console.log('[Stream] Starting to process resume agent responses...');

        for await (const agentResponse of stream) {
          console.log('[Stream] Received agent response:', agentResponse);

          // Skip heartbeat messages
          if (agentResponse.node === "heartbeat") {
            console.log('[Stream] Skipping heartbeat message');
            continue;
          }

          // Check message first: when the Studio sends both chunk and message in the
          // same final event, we treat the complete message as authoritative and ignore
          // the chunk to prevent the content from being appended a second time.
          if (agentResponse.message) {
            console.log('[Stream] Processing message:', agentResponse.message);

            const backendMessage = fromMessageDTO(agentResponse.message);
            const messageType = agentResponse.message.messageType;

            if (messageType === 'assistant' || messageType === 'tool-request') {
              if (isFirstChunk) {
                const newMessage: UIMessage = {
                  id: `${messageType}-${Date.now()}`,
                  message: backendMessage,
                  timestamp: Date.now()
                };
                setMessages((prev) => [...prev, newMessage]);
                isFirstChunk = false;
              } else {
                setMessages((prev) => {
                  // message is authoritative — replace accumulated streaming content entirely
                  const newMessages = [...prev];
                  const lastMessage = newMessages[newMessages.length - 1];
                  newMessages[newMessages.length - 1] = {
                    ...lastMessage,
                    message: {
                      ...backendMessage,
                      content: backendMessage.content || lastMessage.message.content
                    }
                  };
                  return newMessages;
                });
              }
            } else if (messageType === 'tool-confirm' || messageType === 'tool') {
              const newMessage: UIMessage = {
                id: `${messageType}-${Date.now()}`,
                message: backendMessage,
                timestamp: Date.now()
              };
              setMessages((prev) => [...prev, newMessage]);
              isFirstChunk = true;
            }
          } else if (agentResponse.chunk) {
            console.log('[Stream] Processing chunk:', agentResponse.chunk);

            if (isFirstChunk) {
              // Create new assistant message for first chunk
              const newAssistantMessage: UIMessage = {
                id: `assistant-${Date.now()}`,
                message: {
                  messageType: 'assistant',
                  content: agentResponse.chunk,
                  metadata: {},
                  toolCalls: []
                },
                timestamp: Date.now()
              };
              setMessages((prev) => {
                console.log('[Stream] Adding first chunk, prev messages:', prev.length);
                return [...prev, newAssistantMessage];
              });
              isFirstChunk = false;
            } else {
              // Append chunk to existing content
              setMessages((prev) => {
                const newMessages = [...prev];
                const lastMessage = newMessages[newMessages.length - 1];
                newMessages[newMessages.length - 1] = {
                  ...lastMessage,
                  message: {
                    ...lastMessage.message,
                    content: lastMessage.message.content + agentResponse.chunk
                  }
                };
                return newMessages;
              });
            }
          }
        }

        console.log('[Stream] Resume streaming complete');
      } catch (error: any) {
        if (error.name === "AbortError") {
          console.log("Resume request was aborted");
          toast.info("Request cancelled");
        } else {
          console.error("Failed to resume with feedback:", error);
          toast.error(error.message || "Failed to resume execution");
        }
      } finally {
        setIsStreaming(false);
        abortControllerRef.current = null;
      }
    },
    [currentThreadId, appName, userId, mode]
  );

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, []);

  const contextValue = useMemo(() => ({
    messages,
    isStreaming,
    sendMessage,
    resumeFeedback,
    clearMessages,
  }), [messages, isStreaming, sendMessage, resumeFeedback, clearMessages]);

  return (
    <StreamContext.Provider value={contextValue}>
      {children}
    </StreamContext.Provider>
  );
};

// Configuration component (optional, can be used for settings)
export const StreamConfigurationView = () => {
  const [apiUrl, setApiUrl] = useQueryState("apiUrl", {
    defaultValue: process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080",
  });

  const [appName, setAppName] = useQueryState("appName", {
    defaultValue: process.env.NEXT_PUBLIC_APP_NAME || "",
  });

  const [userId, setUserId] = useQueryState("userId", {
    defaultValue: process.env.NEXT_PUBLIC_USER_ID || "user-001",
  });

  return (
    <div className="flex flex-col gap-4 p-4 border rounded-lg">
      <h3 className="text-lg font-semibold">Configuration</h3>

      <div className="flex flex-col gap-2">
        <Label htmlFor="apiUrl">API URL</Label>
        <div className="flex gap-2">
          <Input
            id="apiUrl"
            value={apiUrl || ""}
            onChange={(e) => setApiUrl(e.target.value)}
            placeholder="http://localhost:8080"
          />
        </div>
      </div>

      <div className="flex flex-col gap-2">
        <Label htmlFor="appName">Application Name</Label>
        <div className="flex gap-2">
          <Input
            id="appName"
            value={appName || ""}
            onChange={(e) => setAppName(e.target.value)}
            placeholder="e.g. sales_agent"
          />
        </div>
      </div>

      <div className="flex flex-col gap-2">
        <Label htmlFor="userId">User ID</Label>
        <div className="flex gap-2">
          <Input
            id="userId"
            value={userId || ""}
            onChange={(e) => setUserId(e.target.value)}
            placeholder="user-001"
          />
        </div>
      </div>

      <div className="text-sm text-muted-foreground">
        <p>Current configuration:</p>
        <ul className="list-disc list-inside">
          <li>API URL: {apiUrl}</li>
          <li>App Name: {appName}</li>
          <li>User ID: {userId}</li>
        </ul>
      </div>
    </div>
  );
};

