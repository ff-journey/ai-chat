import { UIMessage, UserMessage } from "@/types/messages";

export function HumanMessage({
  message,
}: {
  message: UIMessage;
  isLoading?: boolean;
}) {
  const userMsg = message.message as UserMessage;
  const contentString = userMsg.content;
  const media = userMsg.media || [];

  return (
    <div className="group ml-auto flex items-center gap-2">
      <div className="flex flex-col items-end gap-2">
        {media.map((m, i) => {
          // data 可能是 base64 裸串、data: URL 或 http/相对路径 URL
          const src =
            m.data.startsWith("http") ||
            m.data.startsWith("blob:") ||
            m.data.startsWith("/") ||
            m.data.startsWith("data:")
              ? m.data
              : `data:${m.mimeType};base64,${m.data}`;
          return (
            <img
              key={i}
              src={src}
              alt="uploaded image"
              className="rounded-xl max-h-48 max-w-xs object-contain border"
            />
          );
        })}
        {contentString && (
          <p className="bg-muted ml-auto w-fit rounded-3xl px-4 py-2 text-right whitespace-pre-wrap">
            {contentString}
          </p>
        )}
      </div>
    </div>
  );
}

