import { Component, type ErrorInfo, type ReactNode } from 'react';

/**
 * 화면을 그리다 난 오류를 받아낸다.
 *
 * 이게 없으면 React 가 트리 전체를 버려서 화면이 백지가 된다.
 * 근무 중에 앱이 하얗게 변하면 간호사는 무엇을 해야 할지 알 수 없고,
 * 결국 전화기를 든다. 이 프로젝트가 없애려던 바로 그 전화다.
 *
 * 오류 내용은 화면에 내보내지 않는다. 스택 트레이스는 쓰는 사람에게 아무 의미가 없고,
 * 파일 경로나 내부 구조가 그대로 드러난다. 개발자는 콘솔에서 본다.
 *
 * 훅으로는 만들 수 없다. React 가 클래스 컴포넌트에만 이 기능을 준다.
 */
interface Props {
  children: ReactNode;
}

interface State {
  failed: boolean;
}

export default class ErrorBoundary extends Component<Props, State> {
  state: State = { failed: false };

  static getDerivedStateFromError(): State {
    return { failed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('화면 오류:', error, info.componentStack);
  }

  render() {
    if (!this.state.failed) return this.props.children;

    return (
      <div className="flex min-h-dvh flex-col items-center justify-center gap-4 p-6 text-center">
        <p className="text-lg font-bold text-slate-900">화면을 표시하지 못했습니다</p>
        <p className="max-w-xs text-sm text-slate-500">
          잠시 후 다시 시도해 주세요. 계속 이러면 이전 화면으로 돌아가 주세요.
        </p>
        <button
          type="button"
          // 상태를 되돌리는 것으로는 부족하다. 무엇이 깨졌는지 모르는 상태라
          // 처음부터 다시 받아오는 편이 확실하다.
          onClick={() => window.location.reload()}
          className="rounded-xl bg-sky-600 px-5 py-2.5 text-sm font-semibold text-white"
        >
          다시 불러오기
        </button>
      </div>
    );
  }
}
