import { HttpResponse } from '@angular/common/http';

/** Saves a downloaded file under the name the server suggested. */
export function saveDownload(response: HttpResponse<Blob>, fallbackName: string): void {
  if (!response.body) {
    return;
  }
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const url = URL.createObjectURL(response.body);
  const link = document.createElement('a');
  link.href = url;
  link.download = /filename="([^"]+)"/.exec(disposition)?.[1] ?? fallbackName;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
