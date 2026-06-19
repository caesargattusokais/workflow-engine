import { createContext, useContext, useState, useCallback, type ReactNode } from 'react';
import zh from './zh';
import en from './en';
import type { Translations } from './zh';

type Lang = 'zh' | 'en';

const langs: Record<Lang, Translations> = { zh, en };

function detectLang(): Lang {
  const stored = localStorage.getItem('lang');
  if (stored === 'zh' || stored === 'en') return stored;
  const nav = navigator.language || '';
  return nav.startsWith('zh') ? 'zh' : 'en';
}

interface I18nCtx {
  lang: Lang;
  t: Translations;
  setLang: (l: Lang) => void;
}

const Ctx = createContext<I18nCtx>({ lang: 'zh', t: zh, setLang: () => {} });

export function I18nProvider({ children }: { children: ReactNode }) {
  const [lang, setLang] = useState<Lang>(detectLang);
  const switchLang = useCallback((l: Lang) => {
    setLang(l);
    localStorage.setItem('lang', l);
  }, []);
  return <Ctx.Provider value={{ lang, t: langs[lang], setLang: switchLang }}>{children}</Ctx.Provider>;
}

export function useT() {
  return useContext(Ctx);
}

export default function LanguageSwitcher() {
  const { lang, setLang } = useT();
  return (
    <div className="flex items-center gap-0.5 text-xs text-gray-400">
      <button onClick={() => setLang('zh')}
        className={`px-1.5 py-0.5 rounded ${lang === 'zh' ? 'bg-blue-600 text-white' : 'hover:text-gray-300'}`}>中</button>
      <button onClick={() => setLang('en')}
        className={`px-1.5 py-0.5 rounded ${lang === 'en' ? 'bg-blue-600 text-white' : 'hover:text-gray-300'}`}>EN</button>
    </div>
  );
}
