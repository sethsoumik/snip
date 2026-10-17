const form = document.getElementById('snip-form');
const input = document.getElementById('url');
const result = document.getElementById('result');
const shortLink = document.getElementById('short-link');
const copyBtn = document.getElementById('copy');
const errorEl = document.getElementById('error');
const submitBtn = document.getElementById('snip-btn');

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  const url = input.value.trim();
  if (submitBtn.disabled) return;

  errorEl.hidden = true;
  result.hidden = true;

  if (!url) {
    errorEl.textContent = 'please enter a URL to shorten';
    errorEl.hidden = false;
    return;
  }

  submitBtn.disabled = true;

  try {
    const res = await fetch('/api/v1/links', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url }),
    });

    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.message || 'Failed to shorten link');
    }

    shortLink.href = data.shortUrl;
    shortLink.textContent = data.shortUrl;
    result.hidden = false;
  } catch (err) {
    errorEl.textContent = err.message;
    errorEl.hidden = false;
  } finally {
    submitBtn.disabled = false;
  }
});

copyBtn.addEventListener('click', async () => {
  const text = shortLink.textContent;
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    copyBtn.textContent = 'copied';
  } catch {
    copyBtn.textContent = 'copy failed';
  }
  setTimeout(() => (copyBtn.textContent = 'copy'), 1500);
});
