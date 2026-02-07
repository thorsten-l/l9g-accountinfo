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

// ----------------------------------------------------------------------------
// -- WebSocket ---------------------------------------------------------------
// ----------------------------------------------------------------------------

import { createLogger } from './logger.js';
import { dict, switchLang, defaultLang } from './i18n.js';
import { activateSignaturePad, resizeCanvas, signaturePad, padUuid } from './signaturePad.js';
import { showAlert } from './alerts.js';
import { showUserinfo } from './userInfo.js';

var ws;
var lastHeartbeatTimestamp = null;

const log = createLogger("websocket");
const bodyEl = document.body;
const wsBaseUrl = bodyEl.getAttribute('data-ws-base-url') || 'unknown-ws-base-url';
const heartbeatEnabled = bodyEl.getAttribute('data-heartbeat-enabled') === 'true';

function formatTimestamp(ts)
{
  const d = new Date(ts);
  const pad2 = n => String(n).padStart(2, '0');

  const yyyy = d.getFullYear();
  const mm = pad2(d.getMonth() + 1);
  const dd = pad2(d.getDate());

  const HH = pad2(d.getHours());
  const MM = pad2(d.getMinutes());
  const SS = pad2(d.getSeconds());

  return `${yyyy}-${mm}-${dd} ${HH}:${MM}:${SS}`;
}

function setStatusLight(status)
{
  if (status)
  {
    document.getElementById("green-light").style.display = "inline-flex";
    document.getElementById("red-light").style.display = "none";
  }
  else
  {
    document.getElementById("green-light").style.display = "none";
    document.getElementById("red-light").style.display = "inline-flex";
    activateSignaturePad(false);
  }
}

function checkHeartbeat()
{
  log.debug("checkHeartbeat");
  if (lastHeartbeatTimestamp && (Date.now() - lastHeartbeatTimestamp > 30000))
  {
    activateSignaturePad(false);
    log.warn("No heartbeat received for 30 seconds. Reconnecting...");
    showAlert("alert.error.connectionLost.title", "alert.error.connectionLost.text", "error");
    ws.close();
  }
}

function connect()
{
  ws = new WebSocket(
          wsBaseUrl + "/ws/signature-pad", ["SIGNATURE_PAD_UUID", padUuid]);

  ws.onmessage = function (event)
  {
    var dtoEvent = JSON.parse(event.data);
    log.debug("Received event: ", dtoEvent);

    if (dtoEvent.event === "heartbeat")
    {
      document.getElementById("heartbeat").innerHTML
              = formatTimestamp(dtoEvent.timestamp);
      lastHeartbeatTimestamp = Date.now();
    }

    if (dtoEvent.event === "show")
    {
      clearPage();
      log.debug("show event received");
      // try
      showUserinfo(dtoEvent.message).then(() => {
        activateSignaturePad(true);
        resizeCanvas();
        signaturePad.clear();
      }).catch(error => {
        log.error('Fehler beim Laden der Userinfo:', error);
        if (error.status === 404)
        {
          document.dispatchEvent(new CustomEvent('signatureSubmitted'));
          showAlert("alert.error.userNotFound.title", "alert.error.userNotFound.text", "error");
        }
      });
      signaturePad.clear();
    }

    if (dtoEvent.event === "hide")
    {
      log.debug("hide event received");
      switchLang(defaultLang);
      clearPage();
      signaturePad.clear();
      activateSignaturePad(false);
      document.dispatchEvent(new CustomEvent('signatureSubmitted'));
    }

    if (dtoEvent.event === "error")
    {
      setStatusLight(false);
      showAlert("ERROR", dtoEvent.message, "error");
      switchLang(defaultLang);
    }
  };

  ws.onopen = function ()
  {
    setStatusLight(true);
    switchLang(defaultLang);
    log.info("WebSocket connection opened.");
    showAlert("alert.websocket.open.title", "alert.websocket.open.text", "success");
    lastHeartbeatTimestamp = Date.now();
  };

  ws.onclose = function ()
  {
    log.info("WebSocket connection closed.");
    activateSignaturePad(false);
    setStatusLight(false);
    
    showAlert("alert.error.connectionLost.title", "alert.error.connectionLost.text", "error");
    reconnect();
  };

  ws.onerror = function (error)
  {
    setStatusLight(false);
    log.error("WebSocket Error");
    switchLang(defaultLang);
    showAlert("alert.error.websocket.title", "alert.error.websocket.text", "error");
  };
}

function reconnect()
{
  log.debug("reconnect");
  setTimeout(connect, 10000);
}

// ----------------------------------------------------------------------------
// -- Start -------------------------------------------------------------------
// ----------------------------------------------------------------------------

window.onload = function ()
{
  log.info("startup signatur pad app");
  switchLang(defaultLang);
  if (padUuid === '*undefined*')
  {
    log.error("ERROR: signatur pad is unregistered");
    document.getElementById("signature-pad-container").style.display = "none";

    Swal.fire(
    {
      title: 'ERROR',
      html: 'This signature pad is unregistered!<br/><br/>Please contact your administrator.',
      icon: 'error',

      showConfirmButton: false,
      showCancelButton: false,

      allowOutsideClick: false,
      allowEscapeKey: false,

      timer: undefined
    });
  }
  else
  {
    log.info("signatur pad running");
    connect();
    if (heartbeatEnabled)
    {
      setInterval(checkHeartbeat, 15000);
    }
  }
};
