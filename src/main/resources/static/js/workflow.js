/**
 * @file This file handles the user workflow for the ID card scanning process,
 * including barcode detection, user info fetching, and photo uploads.
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 * @copyright Thorsten Ludewig 2025
 * @license Apache-2.0
 */

import { createLogger } from './logger.js';
import { fetchUserInfo, userInfo, fetchPersons } from './userInfo.js';
import { activateSignaturePad, resizeCanvas, signaturePad, padUuid } from './signaturePad.js';
import { showAlert } from './alerts.js';

const log = createLogger("workflow");

// --- DOM Element References ---
const barcodeDetector = new BarcodeDetector({formats: ['code_39']});
const video = document.getElementById('video');
const btnNext = document.getElementById('btn-next');
const message = document.getElementById('message');
const scanner = document.getElementById('scanner');
const photoSec = document.getElementById('photo-section');
const photoMsg = document.getElementById('photo-msg');
const btnFront = document.getElementById('btn-front');
const btnBack = document.getElementById('btn-back');
const inputF = document.getElementById('file-front');
const inputB = document.getElementById('file-back');
const previewF = document.getElementById('preview-front');
const previewB = document.getElementById('preview-back');
const imgFront = document.getElementById('img-front');
const imgBack = document.getElementById('img-back');
const commonNameInput = document.getElementById('common-name-input');
const commonNameResults = document.getElementById('common-name-results');
const btnClearCommonName = document.getElementById('btn-clear-common-name');
const btnSendCommonName = document.getElementById('btn-send-common-name');

// --- State Variables ---
var barcodeCounter = 0;
var validBarcode = false;
var customerNumber = null;
var uploadedFront = false;
var uploadedBack = false;
var scanIntervalId = null;
var selectedCustomerNumber = null;

/**
 * Handles input on the common-name-input field, triggering personSearch if the input
 * length is 5 or more.
 */
function handleCommonNameInput() {
  const input = commonNameInput.value.trim();
  if (input.length >= 5) {
    personSearch(input);
  } else {
    commonNameResults.classList.add('d-none'); // Hide results if input is too short
    commonNameResults.innerHTML = ''; // Clear previous results
  }
}

/**
 * Handles selection from the common-name-results dropdown.
 */
function handleCommonNameSelection() {
  const selectedOption = commonNameResults.options[commonNameResults.selectedIndex];
  if (selectedOption) {
    commonNameInput.value = selectedOption.text;
    selectedCustomerNumber = selectedOption.value;
    log.debug("Selected customer number:", selectedCustomerNumber);
    commonNameResults.classList.add('d-none'); // Hide results after selection
  }
}

/**
 * Resets the page state to its initial values.
 */
function resetPage()
{
  validBarcode = false;
  customerNumber = null;
  uploadedFront = false;
  uploadedBack = false;
  
  commonNameInput.value = '';
  commonNameResults.innerHTML = '';
  commonNameResults.classList.add('d-none');
  selectedCustomerNumber = null;
  
  clearPage();
}

/**
 * Displays a message in the main message area.
 * @param {string} text - The message text to display.
 * @param {string} type - The alert type (e.g., 'info', 'success', 'danger').
 */
function showMsg(text, type)
{
  message.textContent = text;
  message.className = `mt-3 alert alert-${type}`;
  message.classList.remove('d-none');
}

/**
 * Displays a message in the photo section message area.
 * @param {string} text - The message text to display.
 * @param {string} type - The alert type (e.g., 'info', 'success', 'danger').
 */
function showPhotoMsg(text, type)
{
  photoMsg.textContent = text;
  photoMsg.className = `mt-2 alert alert-${type}`;
}

/**
 * Processes a scanned card number, fetches user info, and displays the photo section.
 * @param {string} code - The scanned card number.
 * @returns {Promise<void>}
 */
function processCardNumber(code)
{
  log.debug("processCardNumber code=", code);
  
  return fetchUserInfo(code, padUuid).then(_userInfo => {
    if (!validBarcode & (validBarcode = true))
    {
      customerNumber = code;

      barcodeCounter = barcodeCounter + 1;
      log.debug("Number detected:", customerNumber);
      log.debug("processCardNumber");
      log.debug(userInfo);

      if (userInfo.status && userInfo.status !== "OK")
      {
        validBarcode = false;
        return Promise.reject(new Error(userInfo.status));
      }
      else
      {
        customerNumber = userInfo.customer;
      }

      stopScanner();

      document.getElementById('userinfo-name-preview').innerText = `${userInfo.firstname} ${userInfo.lastname}`;
      document.getElementById('userinfo-birthday-preview').innerText = `${userInfo.birthday}`;
      document.getElementById('userinfo-semester-preview').innerText = "";
      document.getElementById('userinfo-home-preview').innerText = "";

      if (userInfo.semester)
      {
        const sem = userInfo.semester;
        const semText = [sem.co, sem.street, sem.zip, sem.city, sem.state, sem.country]
                .filter(Boolean)
                .join(', ');
        document.getElementById('userinfo-semester-preview').innerText = semText;
      }

      if (userInfo.home)
      {
        const home = userInfo.home;
        const homeText = [home.co, home.street, home.zip, home.city, home.state, home.country]
                .filter(Boolean)
                .join(', ');
        document.getElementById('userinfo-home-preview').innerText = homeText;
      }

      clearPage();
      showMsg('Karte akzeptiert', 'success');
      photoSec.classList.remove('d-none');
    }
  });
}

/**
 * Performs a single barcode scan action.
 */
const scan = () => {
  barcodeDetector.detect(video).then(barcodes => {
    if (barcodes.length > 0)
    {
      const detectedCardNumber = barcodes[0].rawValue;
      log.debug(detectedCardNumber);
      const codePattern = /^\d{10}$|^\d{12}$/;
      if (!validBarcode && codePattern.test(detectedCardNumber))
      {
        showMsg(`Code ${detectedCardNumber} erkannt, prüfe...`, 'info');

        processCardNumber(detectedCardNumber).catch(error => {
          showMsg(`Fehler: ${error.message}. Erneut scannen...`, 'danger');
        });
      }
    }
  }).catch(err => {
    log.error("Barcode detection failed:", err);
  });
};

/**
 * Starts the barcode scanning interval if not already running.
 */
function startScanner()
{
  if (!scanIntervalId)
  {
    scanIntervalId = setInterval(scan, 200);
  }
}

/**
 * Stops the barcode scanning interval.
 */
function stopScanner()
{
  if (scanIntervalId)
  {
    clearInterval(scanIntervalId);
    scanIntervalId = null;
  }
}

/**
 * Handles the manual submission of a barcode from the input field.
 */
function handleSendBarcode()
{
  const barcodeInput = document.getElementById('library-barcode');
  const barcodeValue = barcodeInput.value;

  log.debug("handleSendBarcode barcodeValue=", barcodeValue);

  // resetPage();

  if (barcodeValue && ( barcodeValue.trim().length === 8 
          || barcodeValue.trim().length === 10 
          || barcodeValue.trim().length === 12 ) )
  {
    const code = barcodeValue.trim();
    log.debug(`Code ${code} wird geprüft...`);
    showMsg(`Code ${code} wird geprüft...`, 'info');

    processCardNumber(code)
            .catch(error => {
              showAlert("FEHLER", `${error.message}`, "error");
            });
  }
  else
  {
    showAlert("FEHLER", "Bitte gib eine gültige Nummer ein.", "error");
    showStartPage();
  }
}

/**
 * Checks if both front and back photos have been uploaded and shows the next button.
 */
function checkAllUploaded()
{
  if (uploadedFront && uploadedBack)
  {
    btnNext.classList.remove('d-none');
  }
}

/**
 * Uploads a photo to the server.
 * @param {string} side - The side of the ID card ('front' or 'back').
 * @param {File} file - The image file to upload.
 */
function uploadPhoto(side, file)
{
  log.debug("upload photo");
  const fd = new FormData();

  /*
   *      sub: userInfo.uid,
   name: `${userInfo.firstname} ${userInfo.lastname}`,
   mail: userInfo.mail,
   */

  fd.append('fullname', `${userInfo.firstname} ${userInfo.lastname}`);
  fd.append('userid', userInfo.uid);
  fd.append('mail', userInfo.mail);

  fd.append('paduuid', padUuid);
  fd.append('side', side);
  fd.append('file', file, file.name);

  showPhotoMsg('Sende ' + side + '…', 'info');
  fetch('/api/v1/signature-pad/photo', {method: 'POST', body: fd}
  ).then(r => r.json()
  ).then(() => showPhotoMsg('Seite ' + side + ' hochgeladen', 'success')
  ).catch(e => showPhotoMsg('Upload-Fehler ' + side + ': ' + e, 'danger'));
}

/**
 * Handles the file input change event for photos.
 * @param {string} side - The side of the ID card ('front' or 'back').
 * @param {File} file - The selected image file.
 */
function handlePhoto(side, file)
{
  if (!customerNumber || !file)
    return;
  const reader = new FileReader();
  reader.onload = e => {
    if (side === 'front')
    {
      imgFront.src = e.target.result;
      previewF.style.display = 'block';
      uploadedFront = true;
    }
    else
    {
      imgBack.src = e.target.result;
      previewB.style.display = 'block';
      uploadedBack = true;
    }
    checkAllUploaded();
  };
  reader.readAsDataURL(file);

  uploadPhoto(side, file);
}

/**
 * Displays the initial start page view.
 */
function showStartPage()
{
  resetPage();
  document.getElementById('start-page').classList.remove('d-none');
  document.getElementById('signature-pad-title').classList.remove('d-none');
  document.getElementById('scanner-pages').classList.remove('d-none');
}

/**
 * Displays the barcode scanner view and starts scanning.
 */
function showScanner()
{
  resetPage();
  document.getElementById('scanner').classList.remove('d-none');
  document.getElementById('signature-pad-title').classList.remove('d-none');

  startScanner();
}

/**
 * Displays the final signature pad view with all user information.
 */
function showSignaturePad()
{
  log.debug("showSignaturePad");
  clearPage();
  document.getElementById('scanner-pages').classList.add('d-none');
  log.debug(userInfo);

  // Populate user info fields for the signature pad
  document.getElementById('userinfo-name').innerText =
          `${userInfo.firstname} ${userInfo.lastname}`;

  document.getElementById('userinfo-birthday').innerText =
          userInfo.birthday || '';

  document.getElementById('userinfo-email').innerText =
          userInfo.mail || '';

  if (userInfo.semester)
  {
    const sem = userInfo.semester;
    const semText = [sem.co, sem.street, sem.zip, sem.city, sem.state, sem.country]
            .filter(Boolean)
            .join(', ');
    document.getElementById('userinfo-semester').innerText = semText;
    document.getElementById('userinfo-semester-container').style.display = 'block';
  }
  else
  {
    document.getElementById('userinfo-semester-container').style.display = 'none';
  }

  if (userInfo.home)
  {
    const home = userInfo.home;
    const homeText = [home.co, home.street, home.zip, home.city, home.state, home.country]
            .filter(Boolean)
            .join(', ');
    document.getElementById('userinfo-home').innerText = homeText;
    document.getElementById('userinfo-home-container').style.display = 'block';
  }
  else
  {
    document.getElementById('userinfo-home-container').style.display = 'none';
  }

  // Activate the signature pad component
  activateSignaturePad(true);
  resizeCanvas();
}

function personSearch( query )
{
  log.debug("personSearch query=", query);

  // Clear previous results
  commonNameResults.innerHTML = '';
  selectedCustomerNumber = null;

  fetchPersons(query, padUuid)
    .then(persons => {
      log.debug("personSearch - persons:", persons);
      if (persons && persons.length > 0) {
        persons.forEach(person => {
          const option = document.createElement('option');
          option.value = person.customer; // Use customer number as the value
          option.textContent = `${person.firstname}, ${person.lastname}, ${person.birthday || ''}`; // Display name and birthday
          commonNameResults.appendChild(option);
        });
        log.debug("personSearch - Removing 'd-none' class from commonNameResults.");
        commonNameResults.classList.remove('d-none');
      } else {
        log.debug("personSearch - Adding 'd-none' class to commonNameResults.");
        commonNameResults.classList.add('d-none');
      }
    })
    .catch(error => {
      log.error("personSearch - Error fetching persons:", error);
      commonNameResults.classList.add('d-none');
      commonNameResults.innerHTML = '';
      showAlert("FEHLER", `Personensuche fehlgeschlagen: ${error.message}`, "error");
    });
}

log.info("workflow started - log level: " + jsLogLevel);

// --- Event Listeners for Photo Workflow ---
btnFront.addEventListener('click', () => inputF.click());
btnBack.addEventListener('click', () => inputB.click());

inputF.addEventListener('change', () => handlePhoto('front', inputF.files[0]));
inputB.addEventListener('change', () => handlePhoto('back', inputB.files[0]));

// --- Event Listeners for Page Navigation and Actions ---
document.getElementById('btn-show-scanner').addEventListener('click', showScanner);
document.getElementById('btn-cancel-scan').addEventListener('click', showStartPage);
document.getElementById('btn-cancel-photo').addEventListener('click', showStartPage);
document.getElementById('btn-send-barcode').addEventListener('click', handleSendBarcode);
document.getElementById('btn-next').addEventListener('click', showSignaturePad);
document.addEventListener('signatureSubmitted', showStartPage);

// --- Event Listeners for Common Name Search ---
commonNameInput.addEventListener('keyup', handleCommonNameInput);
commonNameResults.addEventListener('change', handleCommonNameSelection);

btnClearCommonName.addEventListener('click', () => {
  commonNameInput.value = '';
  commonNameResults.innerHTML = '';
  commonNameResults.classList.add('d-none');
  selectedCustomerNumber = null;
});

btnSendCommonName.addEventListener('click', () => {
  if (selectedCustomerNumber) {
    log.debug("Sending selected customer number:", selectedCustomerNumber);
    processCardNumber(selectedCustomerNumber)
            .catch(error => {
              showAlert("FEHLER", `${error.message}`, "error");
            });
  } else {
    showAlert("FEHLER", "Bitte wähle einen Namen aus der Liste aus.", "error");
  }
});


/**
 * Initializes the camera and barcode detector when the DOM is fully loaded.
 */
document.addEventListener('DOMContentLoaded', () => {

  if (!('BarcodeDetector' in window))
  {
    showMsg('BarcodeDetector nicht unterstützt', 'danger');
    return;
  }

  if (!barcodeDetector)
  {
    log.error('Barcode-Formate nicht unterstützt');
    return;
  }

  // Initialize camera stream
  navigator.mediaDevices
          .getUserMedia({video: {facingMode: 'environment'}}
          ).then(stream => {

    video.srcObject = stream;

    video.play().catch(err => log.error("Play failed:", err));

  }).catch(err => log.error('Kamera-Fehler: ', err));
});
