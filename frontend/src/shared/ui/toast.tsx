import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';

/**
 * 화면 위에 잠깐 뜨는 알림.
 *
 * 두 가지가 여기로 들어온다.
 *   - 남이 한 일 (실시간으로 들어온 변화)
 *   - 내가 한 일이 됐다는 확인
 *
 * 한 곳에서 그리는 이유는 둘이 따로 쌓이면 화면 같은 자리에서 서로 겹치기 때문이다.
 *
 * 내가 한 일에 확인을 띄우는 이유:
 * 상태 뱃지가 바뀌는 것으로 알 수는 있지만, 폰에서는 누르는 버튼이 화면 아래에 있고
 * 뱃지는 맨 위에 있다. 이동 중에 한 손으로 누르면 바뀐 것을 못 보고 지나친다.
 * "눌린 게 맞나" 하고 한 번 더 누르는 것이 제일 나쁘다.
 */
export type ToastTone = 'info' | 'success' | 'urgent';

interface Toast {
  key: number;
  title: string;
  body?: string;
  tone: ToastTone;
}

interface ToastApi {
  show: (title: string, options?: { body?: string; tone?: ToastTone }) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

/** 확인 문구는 잠깐이면 된다. 오래 떠 있으면 화면을 가린다. */
const LIFETIME_MS = 4000;

/** 겹쳐 쌓이면 아래가 안 보인다. 최근 것만 남긴다. */
const MAX_VISIBLE = 3;

const TONE_STYLE: Record<ToastTone, string> = {
  info: 'bg-slate-900/95 text-white ring-slate-700',
  success: 'bg-emerald-600/95 text-white ring-emerald-700',
  urgent: 'bg-red-600 text-white ring-red-700',
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const show = useCallback<ToastApi['show']>((title, options) => {
    const toast: Toast = {
      key: Date.now() + Math.random(),
      title,
      body: options?.body,
      tone: options?.tone ?? 'info',
    };
    setToasts((prev) => [...prev, toast].slice(-MAX_VISIBLE));
    window.setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.key !== toast.key));
    }, LIFETIME_MS);
  }, []);

  const api = useMemo(() => ({ show }), [show]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      {/*
        pointer-events-none 이 없으면 이 띠가 화면 위쪽의 버튼을 가려 클릭을 먹는다.
        검사실 화면의 상단 메뉴가 바로 이 자리에 있다.
      */}
      <div
        role="status"
        aria-live="polite"
        className="pointer-events-none fixed inset-x-0 top-2 z-50 mx-auto flex max-w-md flex-col gap-2 px-3"
      >
        {toasts.map((toast) => (
          <div key={toast.key} className={`rounded-xl px-4 py-3 shadow-lg ring-1 ${TONE_STYLE[toast.tone]}`}>
            <p className="text-sm font-bold">{toast.title}</p>
            {toast.body && <p className="text-xs opacity-90">{toast.body}</p>}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const api = useContext(ToastContext);
  if (!api) throw new Error('ToastProvider 안에서만 쓸 수 있습니다.');
  return api;
}
