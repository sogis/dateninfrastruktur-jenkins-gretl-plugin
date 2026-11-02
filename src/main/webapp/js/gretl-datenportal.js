(function () {
    "use strict";

    function onReady(callback) {
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", callback);
            return;
        }
        callback();
    }

    function all(root, selector) {
        return Array.prototype.slice.call(root.querySelectorAll(selector));
    }

    function closest(element, selector) {
        if (element.closest) {
            return element.closest(selector);
        }
        while (element && element.nodeType === 1) {
            if (element.matches && element.matches(selector)) {
                return element;
            }
            element = element.parentNode;
        }
        return null;
    }

    function escapeHtml(value) {
        return String(value == null ? "" : value)
                .replace(/&/g, "&amp;")
                .replace(/</g, "&lt;")
                .replace(/>/g, "&gt;")
                .replace(/\"/g, "&quot;")
                .replace(/'/g, "&#39;");
    }

    function setHidden(element, hidden) {
        if (!element) {
            return;
        }
        if (hidden) {
            element.setAttribute("hidden", "hidden");
            return;
        }
        element.removeAttribute("hidden");
    }

    function initStartForms() {
        all(document, "[data-gdp-start-form]").forEach(function (form) {
            all(form, "[data-gdp-dataset-selector]").forEach(function (select) {
                select.addEventListener("change", function () {
                    var startUrl = select.getAttribute("data-gdp-dataset-start-url");
                    if (startUrl) {
                        window.location.href = select.value
                                ? startUrl + encodeURIComponent(select.value)
                                : startUrl.replace(/&dataset=$/, "");
                    }
                });
            });

            var modeInput = form.querySelector('[name="PUBLICATION_MODE"]');
            var dataInput = form.querySelector('[name="DATA_FILE"]');
            var issueField = form.querySelector('[data-gdp-field="SERIES_ID"]');
            var issueInput = issueField ? issueField.querySelector('[name="SERIES_ID"]') : null;
            var issueRequiredMark = issueField ? issueField.querySelector('[data-gdp-issue-required]') : null;

            function updateIssueField() {
                if (!issueInput) {
                    return;
                }
                var hasData = !!(dataInput && dataInput.files && dataInput.files.length > 0);
                setHidden(issueField, !hasData);
                setHidden(issueRequiredMark, !hasData);
                issueInput.disabled = !hasData;
                issueInput.required = hasData;
                issueInput.setAttribute("aria-required", hasData ? "true" : "false");
                if (!hasData) {
                    issueInput.value = "";
                }
            }

            function updatePublicationMode() {
                var repositoryMode = modeInput && modeInput.value === "repository-metadata";
                all(form, "[data-gdp-file-input]").forEach(function (input) {
                    input.disabled = repositoryMode;
                    setHidden(closest(input, "[data-gdp-field]"), repositoryMode);
                    if (repositoryMode) input.value = "";
                });
                all(form, "[data-gdp-file-name]").forEach(function (target) {
                    var input = form.querySelector('[name="' + target.getAttribute("data-gdp-file-name") + '"]');
                    target.textContent = input && input.files && input.files.length ? input.files[0].name : "Keine Datei ausgewählt.";
                });
                updateIssueField();
            }
            if (modeInput) modeInput.addEventListener("change", updatePublicationMode);
            updatePublicationMode();
            all(form, "[data-gdp-file-input]").forEach(function (input) {
                input.addEventListener("change", function () {
                    updateIssueField();
                    var field = closest(input, "[data-gdp-field]");
                    var fileName = field ? field.querySelector("[data-gdp-file-name]") : null;
                    if (fileName) {
                        fileName.textContent = input.files && input.files.length > 0
                                ? input.files[0].name
                                : "Keine Datei ausgewählt.";
                    }
                });
            });
            form.addEventListener("reset", function () {
                window.setTimeout(function () {
                    updatePublicationMode();
                    all(form, "[data-gdp-file-name]").forEach(function (target) {
                        target.textContent = "Keine Datei ausgewählt.";
                    });
                }, 0);
            });
        });
    }

    function initRunFilters() {
        all(document, "[data-gdp-run-filters]").forEach(function (form) {
            all(form, "select").forEach(function (control) {
                control.addEventListener("change", function () {
                    form.requestSubmit();
                });
            });
        });
    }

    function renderParameters(container, parameters) {
        if (!container) {
            return;
        }
        if (!parameters || parameters.length === 0) {
            container.innerHTML = "<p class=\"gdp-muted\">Keine Parameter gespeichert.</p>";
            return;
        }

        var rows = parameters.map(function (parameter) {
            return "<tr><th>" + escapeHtml(parameter.name) + "</th><td>" + escapeHtml(parameter.value) + "</td></tr>";
        }).join("");
        container.innerHTML = "<table class=\"pane gdp-table gdp-table--compact\"><tbody>" + rows + "</tbody></table>";
    }

    function renderArtifacts(container, artifacts) {
        if (!container) {
            return;
        }
        var disclosure = container.querySelector("[data-gdp-artifact-disclosure]");
        var wasOpen = disclosure && disclosure.open;
        if (!artifacts || artifacts.length === 0) {
            container.innerHTML = "<div class=\"gdp-panel__header\"><h2>Artefakte</h2></div>"
                    + "<p class=\"gdp-muted\">Keine Artefakte archiviert.</p>";
            return;
        }

        var items = artifacts.map(function (artifact) {
            return "<a class=\"gdp-artifact\" href=\"" + escapeHtml(artifact.url) + "\">"
                    + "<span class=\"gdp-artifact__icon\" aria-hidden=\"true\">Datei</span>"
                    + "<span>" + escapeHtml(artifact.fileName) + "</span>"
                    + "</a>";
        }).join("");
        container.innerHTML = "<details class=\"gdp-artifact-disclosure\" data-gdp-artifact-disclosure=\"true\""
                + (wasOpen ? " open=\"open\"" : "")
                + "><summary class=\"gdp-panel__header gdp-artifact-disclosure__summary\">"
                + "<h2>Artefakte</h2><span class=\"gdp-artifact-count\">(" + artifacts.length + ")</span>"
                + "</summary><div class=\"gdp-artifact-list\">" + items + "</div></details>";
    }

    function renderLog(container, logText, running) {
        if (!container) {
            return;
        }
        if (!logText) {
            container.innerHTML = "<p class=\"gdp-muted\">"
                    + (running ? "Noch keine Logzeilen verfügbar." : "Keine Logzeilen verfügbar.")
                    + "</p>";
            return;
        }

        var pre = document.createElement("pre");
        pre.className = "gdp-log-output";
        pre.textContent = logText;
        container.innerHTML = "";
        container.appendChild(pre);
    }

    function syncRunActionLink(view, linkSelector, placeholderSelector, url) {
        var link = view.querySelector(linkSelector);
        var placeholder = view.querySelector(placeholderSelector);
        if (link) {
            if (url) {
                link.setAttribute("href", url);
            }
            setHidden(link, !url);
        }
        if (placeholder) {
            setHidden(placeholder, !!url);
        }
    }

    function updateRunStatusView(view, payload) {
        var hero = view.querySelector("[data-gdp-run-hero]");
        var badge = view.querySelector("[data-gdp-run-badge]");
        var title = view.querySelector("[data-gdp-run-title]");
        var primaryLabel = view.querySelector("[data-gdp-run-primary-label]");
        var startTime = view.querySelector("[data-gdp-run-start-time]");
        var duration = view.querySelector("[data-gdp-run-duration]");
        var jobName = view.querySelector("[data-gdp-run-job-name]");

        view.setAttribute("data-gdp-status", payload.status);
        view.setAttribute("data-gdp-run-complete", payload.complete ? "true" : "false");
        if (hero) {
            hero.setAttribute("data-gdp-status", payload.status);
        }
        if (badge) {
            badge.setAttribute("data-gdp-status", payload.status);
            badge.textContent = payload.statusLabel || payload.status;
        }
        if (title) {
            title.textContent = payload.headline || payload.jobFullDisplayName;
        }
        if (primaryLabel) {
            primaryLabel.textContent = payload.primaryLabel || "";
        }
        if (startTime) {
            startTime.textContent = payload.startTime || "–";
        }
        if (duration) {
            duration.textContent = payload.duration || "–";
        }
        if (jobName) {
            jobName.textContent = payload.jobFullDisplayName || payload.jobFullName || "";
        }

        renderParameters(view.querySelector("[data-gdp-parameters]"), payload.parameters);
        renderArtifacts(view.querySelector("[data-gdp-artifacts]"), payload.artifacts);
        renderLog(view.querySelector("[data-gdp-log]"), payload.logText, !payload.complete);

        syncRunActionLink(view, "[data-gdp-console-link]", "[data-gdp-console-placeholder]", payload.consoleUrl);
        syncRunActionLink(view, "[data-gdp-console-link-secondary]", "[data-gdp-console-placeholder-secondary]", payload.consoleUrl);
        syncRunActionLink(view, "[data-gdp-artifacts-link]", "[data-gdp-artifacts-placeholder]", payload.artifactsUrl);

        if (payload.detailsUrl && window.history && window.history.replaceState) {
            window.history.replaceState(null, "", payload.detailsUrl);
        }
    }

    function initRunStatusViews() {
        all(document, "[data-gdp-run-status-view]").forEach(function (view) {
            var urlCarrier = view.querySelector("[data-gdp-run-status-url]");
            var pollUrl = urlCarrier ? urlCarrier.getAttribute("data-gdp-value") : "";
            var pollingEnabled = urlCarrier && urlCarrier.getAttribute("data-gdp-poll-enabled") === "true";
            var timerId = 0;

            function stopPolling() {
                if (timerId) {
                    window.clearTimeout(timerId);
                    timerId = 0;
                }
            }

            function scheduleNextPoll() {
                stopPolling();
                timerId = window.setTimeout(refresh, 5000);
            }

            function refresh() {
                if (!pollUrl) {
                    return;
                }

                window.fetch(pollUrl, {
                    credentials: "same-origin",
                    headers: {
                        "Accept": "application/json"
                    }
                }).then(function (response) {
                    if (!response.ok) {
                        throw new Error("HTTP " + response.status);
                    }
                    return response.json();
                }).then(function (payload) {
                    updateRunStatusView(view, payload);
                    if (!payload.complete) {
                        scheduleNextPoll();
                    }
                }).catch(function () {
                    stopPolling();
                });
            }

            if (pollingEnabled) {
                scheduleNextPoll();
            }
        });
    }

    onReady(function () {
        initStartForms();
        initRunFilters();
        initRunStatusViews();
    });
}());
