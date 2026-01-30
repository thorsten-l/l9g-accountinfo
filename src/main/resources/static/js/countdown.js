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

import { showAlert } from './alerts.js';

const total = 180; // Sekunden
let left;
let timer;

export function startCountdown()
{
  left = total;
  const pie = document.getElementById('pie');
  const label = document.getElementById('pie-label');
  // Umfang = 2πr = 2 * π * 45 ≈ 282.743
  const circumference = 2 * Math.PI * 40;

  if (timer)
  {
    clearInterval(timer);
  }

  timer = setInterval(() => {
    left--;
    if (left < 0)
    {
      clearInterval(timer);
      label.textContent = '0 s';
      showAlert("ERROR", "Timeout!", "error");
      document.dispatchEvent(new CustomEvent('signatureSubmitted'));
      return;
    }
    // stroke-dashoffset: von 0 bis circumference
    const offset = circumference * (1 - left / total);
    pie.style.strokeDashoffset = offset;
    label.textContent = left + ' s';
  }, 1000);

  // Initial setzen
  pie.style.strokeDasharray = circumference;
  pie.style.strokeDashoffset = 0;
}

export function stopCountdown()
{
  if (timer)
  {
    clearInterval(timer);
    timer = null; // Reset timer to indicate it's stopped
  }
  // Optionally reset visual elements if needed, but not requested.
}
