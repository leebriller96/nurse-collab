import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { api, messageOf } from '@/shared/api/client';
import type { EncounterFullView, NoteType, NursingNote, PageResponse } from '@/shared/api/types';

const SBAR_FIELDS = [
  { key: 'situation', label: '지금 상황', hint: '22시경 어지러움 호소' },
  { key: 'background', label: '배경', hint: '뇌경색으로 입원 4일차, 낙상 위험 등급 상' },
  { key: 'assessment', label: '판단', hint: '혈압 98/60, 기립성 저혈압 의심' },
  { key: 'recommendation', label: '제안', hint: '야간 이동 시 반드시 동반, 당직의 보고 완료' },
] as const;

type SbarKey = (typeof SBAR_FIELDS)[number]['key'];

const TYPE_LABEL: Record<NoteType, string> = {
  GENERAL: '일반 기록',
  SBAR: 'SBAR',
  HANDOVER: '인수인계',
};

const time = (iso: string) =>
  new Date(iso).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' });

const emptySbar = (): Record<SbarKey, string> =>
  ({ situation: '', background: '', assessment: '', recommendation: '' });

/**
 * W-07 간호기록.
 *
 * SBAR 는 인수인계할 때 "무슨 일이 있었고 / 왜 그런 상태이고 / 어떻게 보고 있고 / 무엇을 해달라"
 * 를 빠짐없이 넘기기 위한 형식이다. 칸을 나눠 두면 빠뜨리기 어려워진다.
 */
export default function NursingNotePage() {
  const { id } = useParams();
  const encounterId = Number(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [noteType, setNoteType] = useState<NoteType>('SBAR');
  const [sbar, setSbar] = useState(emptySbar);
  const [content, setContent] = useState('');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const encounter = useQuery({
    queryKey: ['encounter', encounterId],
    queryFn: async () => (await api.get<EncounterFullView>(`/encounters/${encounterId}`)).data,
  });

  const notes = useQuery({
    queryKey: ['nursing-notes', encounterId],
    queryFn: async () =>
      (await api.get<PageResponse<NursingNote>>(`/encounters/${encounterId}/nursing-notes`,
        { params: { page: 0, size: 30 } })).data,
  });

  const reset = () => {
    setSbar(emptySbar());
    setContent('');
    setEditingId(null);
    setError(null);
  };

  const save = useMutation({
    mutationFn: async () => {
      const body = {
        noteType,
        ...sbar,
        content: content.trim() || null,
        recordedAt: new Date().toISOString(),
      };
      if (editingId) {
        await api.put(`/nursing-notes/${editingId}`, body);
      } else {
        await api.post(`/encounters/${encounterId}/nursing-notes`, body);
      }
    },
    onSuccess: () => {
      reset();
      void queryClient.invalidateQueries({ queryKey: ['nursing-notes', encounterId] });
    },
    onError: (e) => setError(messageOf(e, '저장하지 못했습니다.')),
  });

  const startEdit = (note: NursingNote) => {
    setEditingId(note.id);
    setNoteType(note.noteType);
    setSbar({
      situation: note.situation ?? '',
      background: note.background ?? '',
      assessment: note.assessment ?? '',
      recommendation: note.recommendation ?? '',
    });
    setContent(note.content ?? '');
    setError(null);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const filled = noteType === 'GENERAL'
    ? content.trim() !== ''
    : SBAR_FIELDS.some((f) => sbar[f.key].trim() !== '');

  return (
    <div className="pb-28">
      <header className="sticky top-0 z-10 flex items-center gap-2 bg-slate-100/95 px-4 py-3 backdrop-blur">
        <button type="button" onClick={() => navigate(-1)} className="text-slate-500">
          ←
        </button>
        <h1 className="text-lg font-bold text-slate-900">간호기록</h1>
        {encounter.data && (
          <span className="text-sm text-slate-500">
            {encounter.data.roomNo}-{encounter.data.bedNo} {encounter.data.patient.name}
          </span>
        )}
      </header>

      <section className="mx-3 rounded-xl bg-white p-3.5 shadow-sm ring-1 ring-slate-200">
        {editingId && (
          <p className="mb-3 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900">
            기록을 고치는 중입니다. 고치기 전 내용은 그대로 남습니다.
          </p>
        )}

        <div className="grid grid-cols-3 gap-2">
          {(Object.keys(TYPE_LABEL) as NoteType[]).map((type) => (
            <button
              key={type}
              type="button"
              disabled={editingId !== null}
              onClick={() => setNoteType(type)}
              className={`rounded-lg py-2 text-sm font-semibold ring-1 disabled:opacity-50 ${
                noteType === type
                  ? 'bg-sky-600 text-white ring-transparent'
                  : 'bg-white text-slate-600 ring-slate-200'
              }`}
            >
              {TYPE_LABEL[type]}
            </button>
          ))}
        </div>

        {noteType === 'GENERAL' ? (
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            rows={5}
            placeholder="관찰한 내용을 적습니다."
            className="mt-3 w-full rounded-xl border border-slate-300 p-3 text-base outline-none focus:border-sky-500"
          />
        ) : (
          <div className="mt-3 space-y-3">
            {SBAR_FIELDS.map((field) => (
              <div key={field.key}>
                <label className="block text-sm font-medium text-slate-700" htmlFor={field.key}>
                  {field.label}
                </label>
                <textarea
                  id={field.key}
                  rows={2}
                  value={sbar[field.key]}
                  onChange={(e) => setSbar((v) => ({ ...v, [field.key]: e.target.value }))}
                  placeholder={field.hint}
                  className="mt-1 w-full rounded-xl border border-slate-300 p-3 text-base outline-none focus:border-sky-500"
                />
              </div>
            ))}
          </div>
        )}
      </section>

      {error && (
        <p className="mx-3 mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
          {error}
        </p>
      )}

      <section className="mx-3 mt-4 rounded-xl bg-white p-3.5 shadow-sm ring-1 ring-slate-200">
        <h2 className="mb-2 text-sm font-medium text-slate-600">지난 기록</h2>
        {notes.data?.content.length === 0 ? (
          <p className="py-4 text-center text-sm text-slate-400">아직 기록이 없습니다.</p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {notes.data?.content.map((note) => (
              <li key={note.id} className="py-3">
                <div className="flex items-center gap-2 text-xs text-slate-400">
                  <span className="rounded bg-slate-100 px-1.5 py-0.5 font-medium text-slate-600">
                    {TYPE_LABEL[note.noteType]}
                  </span>
                  <span>{time(note.recordedAt)}</span>
                  <span>{note.recordedBy.name}</span>
                  {note.editable && (
                    <button
                      type="button"
                      onClick={() => startEdit(note)}
                      className="ml-auto text-sky-600"
                    >
                      고치기
                    </button>
                  )}
                </div>

                {note.noteType === 'GENERAL' ? (
                  <p className="mt-1.5 whitespace-pre-wrap text-sm text-slate-700">{note.content}</p>
                ) : (
                  <dl className="mt-1.5 space-y-1 text-sm">
                    {SBAR_FIELDS.map((field) =>
                      note[field.key] ? (
                        <div key={field.key} className="flex gap-2">
                          <dt className="w-16 shrink-0 text-slate-400">{field.label}</dt>
                          <dd className="whitespace-pre-wrap text-slate-700">{note[field.key]}</dd>
                        </div>
                      ) : null,
                    )}
                  </dl>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <div className="fixed inset-x-0 bottom-[var(--app-bottom-bar,0px)] z-10 mx-auto flex max-w-md gap-2 border-t border-slate-200 bg-white p-3">
        {editingId && (
          <button
            type="button"
            onClick={reset}
            className="flex-1 rounded-xl bg-slate-100 py-3.5 text-base font-semibold text-slate-600"
          >
            취소
          </button>
        )}
        <button
          type="button"
          disabled={!filled || save.isPending}
          onClick={() => save.mutate()}
          className="flex-[2] rounded-xl bg-sky-600 py-3.5 text-base font-bold text-white disabled:bg-slate-300"
        >
          {save.isPending ? '저장 중…' : editingId ? '수정 저장' : '기록 남기기'}
        </button>
      </div>
    </div>
  );
}
