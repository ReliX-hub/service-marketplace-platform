import { useCallback, useEffect, useState, type Dispatch, type SetStateAction } from "react";
import { ApiError, demoFallbackEnabled } from "../lib/api";

export interface AsyncData<T> {
  data: T;
  loading: boolean;
  error: ApiError | null;
  demo: boolean;
  reload: () => Promise<void>;
  setData: Dispatch<SetStateAction<T>>;
}

export function useAsyncData<T>(loader: () => Promise<T>, fallback: T, dependencies: unknown[] = []): AsyncData<T> {
  const [data, setData] = useState(fallback);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [demo, setDemo] = useState(false);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      setData(await loader());
      setError(null);
      setDemo(false);
    } catch (caught) {
      const problem = caught instanceof ApiError ? caught : new ApiError("Unable to load data");
      setError(problem);
      if (demoFallbackEnabled && (problem.status === 0 || problem.status >= 500)) {
        setData(fallback);
        setDemo(true);
      }
    } finally {
      setLoading(false);
    }
  }, dependencies);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { data, loading, error, demo, reload, setData };
}
