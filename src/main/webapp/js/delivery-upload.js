'use strict';
const deliveryUpload = document.getElementById('di-upload');
if (deliveryUpload) {
    deliveryUpload.addEventListener('submit', (event) => {
        event.preventDefault();
        const button = deliveryUpload.querySelector('button');
        const message = document.getElementById('di-upload-message');
        const input = deliveryUpload.elements.namedItem('file');
        if (!input || input.files.length !== 1) return;
        const endpoint = new URL(deliveryUpload.dataset.endpoint, window.location.origin);
        if (endpoint.origin !== window.location.origin) return;

        const body = new FormData();
        body.append('file', input.files[0]);
        // Jenkins appends a crumb to the action of multipart forms. The endpoint is
        // separate from the form action and the crumb is passed only in the header.
        const request = new XMLHttpRequest();
        request.open('POST', endpoint.href);
        request.responseType = 'json';
        request.setRequestHeader(deliveryUpload.dataset.crumbField, deliveryUpload.dataset.crumb);
        request.setRequestHeader('Idempotency-Key', deliveryUpload.dataset.key);
        request.setRequestHeader('Accept', 'application/json');
        request.addEventListener('load', () => {
            const result = request.response;
            if (request.status === 201 && result && typeof result.statusUrl === 'string') {
                const target = new URL(result.statusUrl, window.location.origin);
                if (target.origin === window.location.origin) window.location.assign(target.href);
            } else {
                message.textContent = result?.message || 'Die CSV konnte nicht eingereicht werden.';
            }
        });
        request.addEventListener('error', () => {
            message.textContent = 'Der Upload wurde unterbrochen. Bitte den aktuellen Lieferstatus prüfen.';
        });
        request.addEventListener('loadend', () => { button.disabled = false; });
        button.disabled = true;
        message.textContent = 'CSV wird übertragen …';
        request.send(body);
    });
}
