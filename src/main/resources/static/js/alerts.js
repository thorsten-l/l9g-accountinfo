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

/* global Swal */

import { dict } from './i18n.js';

export function showAlert(keyTitle, keyText, icon, ...args) {
  const t = dict; 
  const title = t[keyTitle] || keyTitle;
  let text   = t[keyText]   || keyText;
  args.forEach((v,i) => {
    text = text.replace(`{${i}}`, v);
  });
  Swal.fire({
    title,
    text,
    icon,
    confirmButtonText: 'OK',
    timer: 3000,
    timerProgressBar: true,
    showConfirmButton: false
  });
}

export function showPermanentAlert(keyTitle, keyText, icon, ...args) {
  const t = dict; 
  const title = t[keyTitle] || keyTitle;
  let text   = t[keyText]   || keyText;
  args.forEach((v,i) => {
    text = text.replace(`{${i}}`, v);
  });
  Swal.fire({
    title,
    text,
    icon,
    timerProgressBar: false,
    confirmButtonText: ' OK ',
    showConfirmButton: true
  });
}
