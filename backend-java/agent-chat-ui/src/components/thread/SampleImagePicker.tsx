import React, { useEffect, useState } from "react";
import { createApiClient, SampleCategory } from "@/lib/spring-ai-api";
import { X as XIcon, Loader2 } from "lucide-react";

interface SampleImagePickerProps {
  open: boolean;
  onClose: () => void;
  onSelect: (sampleId: string, previewUrl: string, label: string) => void;
}

export const SampleImagePicker: React.FC<SampleImagePickerProps> = ({
  open,
  onClose,
  onSelect,
}) => {
  const [categories, setCategories] = useState<SampleCategory[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setLoading(true);
    setError(null);
    const apiClient = createApiClient();
    apiClient
      .listSampleImages()
      .then((data) => {
        setCategories(data);
      })
      .catch((err) => {
        setError(err.message || "Failed to load sample images");
        setCategories([]);
      })
      .finally(() => setLoading(false));
  }, [open]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      onClick={onClose}
    >
      <div
        className="relative mx-4 flex max-h-[80vh] w-full max-w-2xl flex-col rounded-xl bg-white shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Fixed header */}
        <div className="flex flex-shrink-0 items-center justify-between rounded-t-xl border-b bg-white px-6 py-4">
          <h2 className="text-lg font-semibold">Sample X-ray Images</h2>
          <button
            onClick={onClose}
            className="rounded-full p-1 hover:bg-gray-100"
          >
            <XIcon className="h-5 w-5" />
          </button>
        </div>

        {/* Scrollable content */}
        <div className="overflow-y-auto p-6">
          {loading && (
            <div className="flex justify-center py-12">
              <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
            </div>
          )}

          {error && (
            <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
              {error}
            </div>
          )}

          {!loading && !error && categories.length === 0 && (
            <div className="rounded-lg border border-dashed p-8 text-center text-muted-foreground">
              No sample images available. Place images in the <code>samples/</code> directory.
            </div>
          )}

          {!loading &&
            categories.map((cat) => (
              <div key={cat.category} className="mb-6">
                <h3 className="mb-3 text-sm font-medium text-gray-700">
                  {cat.label}
                </h3>
                <div className="grid grid-cols-3 gap-3 sm:grid-cols-4">
                  {cat.images.map((img) => {
                    const apiClient = createApiClient();
                    const previewUrl = apiClient.getSampleImageUrl(
                      cat.category,
                      img.filename
                    );
                    const sampleId = `${cat.category}/${img.filename}`;
                    return (
                      <button
                        key={sampleId}
                        className="group relative overflow-hidden rounded-lg border bg-gray-50 transition-all hover:border-green-500 hover:shadow-md"
                        onClick={() => onSelect(sampleId, previewUrl, img.label)}
                      >
                        <img
                          src={previewUrl}
                          alt={img.label}
                          className="h-28 w-full object-cover"
                          loading="lazy"
                        />
                        <div className="p-1 text-center">
                          <span className="truncate text-xs text-gray-600">
                            {img.label}
                          </span>
                        </div>
                      </button>
                    );
                  })}
                </div>
              </div>
            ))}
        </div>
      </div>
    </div>
  );
};
