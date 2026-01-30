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

window.clearPage = function()
{
  console.log("clear page");
  document.getElementById('signature-pad-title').classList.add('d-none');
  document.getElementById('start-page').classList.add('d-none');
  document.getElementById('scanner').classList.add('d-none');
  document.getElementById('message').classList.add('d-none');
  document.getElementById('photo-msg').classList.add('d-none');
  document.getElementById('photo-section').classList.add('d-none');
  document.getElementById('btn-next').classList.add('d-none');
  document.getElementById('preview-front').style.display = 'none';
  document.getElementById('preview-back').style.display = 'none';
  document.getElementById('img-front').src = '';
  document.getElementById('img-back').src = '';
  document.getElementById('signpad-logo-container').classList.add('d-none');
  document.getElementById('signpad-active').style.display = 'none';
};