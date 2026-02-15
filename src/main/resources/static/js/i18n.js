/* 
 * Copyright 2025 Thorsten Ludewig (t.ludewig@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// ----------------------------------------------------------------------------
// -- I18N --------------------------------------------------------------------
// ----------------------------------------------------------------------------

export const defaultLang = 'de';
export let dict = {};

export async function loadDict(lang)
{
  const res = await fetch(`/i18n/${lang}.json`, { redirect: 'manual' });
  if (res.type === 'opaqueredirect') {
    window.location.reload();
    return new Promise(() => {});
  }
  if (!res.ok)
    throw new Error(`Can't load ${lang}.json`);
  return res.json();
}

function applyI18n()
{
  document.querySelectorAll('[data-i18n]').forEach(el => {
    const key = el.getAttribute('data-i18n');
    if (dict[key])
    {
      // title-Attribut (bei <title>) oder innerText
      if (el.tagName.toLowerCase() === 'title')
      {
        document.title = dict[key];
      }
      else
      {
        el.innerText = dict[key];
      }
    }
  });
}


export async function switchLang(lang)
{
  try
  {
    dict = await loadDict(lang);
    localStorage.setItem('lang', lang);
    document.getElementById('lang-flag').src = `i18n/flags/${lang}.svg`;
    applyI18n();
  }
  catch (e)
  {
    console.error(e);
  }
}

document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('#lang-menu button').forEach(btn => {
    btn.addEventListener('click', () => {
      switchLang(btn.dataset.lang);
    });
  });
  const lang = localStorage.getItem('lang') || defaultLang;
  switchLang(lang);
});
