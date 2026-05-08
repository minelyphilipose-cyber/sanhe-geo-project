import DOMPurify from 'dompurify'

export function sanitizeContentHtml(value: string | null | undefined): string {
  return DOMPurify.sanitize(value ?? '', {
    ALLOWED_TAGS: [
      'p', 'br', 'strong', 'em', 'b', 'i', 'ul', 'ol', 'li', 'a', 'img', 'h1', 'h2', 'h3', 'blockquote',
      'table', 'thead', 'tbody', 'tfoot', 'tr', 'th', 'td', 'caption',
      'pre', 'code',
      'details', 'summary',
      'h4', 'h5', 'h6', 'hr', 'span', 'div',
    ],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'target', 'rel', 'colspan', 'rowspan', 'class'],
    ALLOWED_URI_REGEXP: /^(?:(?:https?):|data:image\/(?:png|jpeg|jpg|gif|webp);base64,|[^a-z])/i,
  }).trim()
}
