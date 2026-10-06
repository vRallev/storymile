/* Adapted from App Platform's docs/javascripts/blueprints.js resize controls. */
(() => {
  const preview = document.querySelector("[data-app-preview]");
  if (!preview) {
    return;
  }

  const stage = preview.querySelector("[data-preview-stage]");
  const container = preview.querySelector("[data-preview-container]");
  const handle = preview.querySelector("[data-preview-handle]");
  const size = preview.querySelector("[data-preview-size]");
  const presets = preview.querySelectorAll("[data-preview-preset]");
  const widths = {
    phone: 480,
    tablet: 1100,
    desktop: 1440,
  };
  const height = 840;
  let requestedWidth = widths.phone;
  let selectedPreset = "phone";
  let pointerId = null;

  const bounds = () => {
    const style = getComputedStyle(stage);
    const availableWidth = Math.round(
      stage.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight),
    );
    const maximum = Math.max(1, Math.min(1440, availableWidth));
    return { minimum: Math.min(320, maximum), maximum };
  };

  const render = () => {
    const { minimum, maximum } = bounds();
    const width = Math.round(Math.max(minimum, Math.min(maximum, requestedWidth)));
    container.style.width = `${width}px`;
    container.style.height = `${height}px`;
    handle.setAttribute("aria-valuemin", String(minimum));
    handle.setAttribute("aria-valuemax", String(maximum));
    handle.setAttribute("aria-valuenow", String(width));
    handle.setAttribute("aria-valuetext", `${width} pixels wide`);
    const dimensions = `${width} × ${height}`;
    if (size.textContent !== dimensions) {
      size.textContent = dimensions;
    }
    for (const preset of presets) {
      preset.setAttribute("aria-pressed", String(preset.dataset.previewPreset === selectedPreset));
    }
  };

  const resize = (width) => {
    const { minimum, maximum } = bounds();
    requestedWidth = Math.max(minimum, Math.min(maximum, width));
    selectedPreset = null;
    render();
  };

  for (const preset of presets) {
    preset.addEventListener("click", () => {
      selectedPreset = preset.dataset.previewPreset;
      requestedWidth = widths[selectedPreset];
      render();
    });
  }

  handle.addEventListener("pointerdown", (event) => {
    if (event.button !== 0 || pointerId !== null) {
      return;
    }
    event.preventDefault();
    handle.focus();
    pointerId = event.pointerId;
    const initialWidth = container.getBoundingClientRect().width;
    const initialX = event.clientX;

    const move = (moveEvent) => {
      if (moveEvent.pointerId === pointerId) {
        resize(initialWidth + moveEvent.clientX - initialX);
      }
    };

    const stop = (stopEvent) => {
      if (stopEvent.pointerId !== pointerId) {
        return;
      }
      const capturedPointerId = pointerId;
      pointerId = null;
      preview.classList.remove("app-preview--resizing");
      handle.removeEventListener("pointermove", move);
      handle.removeEventListener("pointerup", stop);
      handle.removeEventListener("pointercancel", stop);
      handle.removeEventListener("lostpointercapture", stop);
      if (handle.hasPointerCapture(capturedPointerId)) {
        handle.releasePointerCapture(capturedPointerId);
      }
    };

    handle.addEventListener("pointermove", move);
    handle.addEventListener("pointerup", stop);
    handle.addEventListener("pointercancel", stop);
    handle.addEventListener("lostpointercapture", stop);
    handle.setPointerCapture(pointerId);
    preview.classList.add("app-preview--resizing");
  });

  handle.addEventListener("keydown", (event) => {
    const { minimum, maximum } = bounds();
    const width = container.getBoundingClientRect().width;
    const step = event.shiftKey ? 96 : 24;
    const values = { ArrowLeft: width - step, ArrowRight: width + step, Home: minimum, End: maximum };
    if (!(event.key in values)) {
      return;
    }
    event.preventDefault();
    resize(values[event.key]);
  });

  // Keep the iframe alive while the outer page changes size.
  new ResizeObserver(render).observe(stage);
  render();
})();
