import { useState } from 'react'

const API = import.meta.env.VITE_API_BASE || 'http://localhost:8080'
const emptySourceForm = {
  title: '', organization: '', documentDate: '', url: '', crops: 'General agriculture',
  category: 'crop_guidance', cropFilter: 'all', regionFilter: 'india', content: '',
  sourceType: 'UPLOAD', fileName: '',
}

async function request(path, options) {
  const response = await fetch(`${API}${path}`, options)
  const payload = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(payload.detail || payload.message || 'Request failed. Please try again.')
  return payload
}

function normalizeAdminToken(value) {
  const pasted = value.trim()
  const token = pasted.startsWith('SOURCE_ADMIN_TOKEN=')
    ? pasted.slice('SOURCE_ADMIN_TOKEN='.length).trim()
    : pasted
  return token.replace(/^["']|["']$/g, '')
}

export default function SourceManager({ onClose, onSaved }) {
  const [token, setToken] = useState('')
  const [unlocked, setUnlocked] = useState(false)
  const [adminSources, setAdminSources] = useState([])
  const [form, setForm] = useState(emptySourceForm)
  const [sourceUrl, setSourceUrl] = useState('')
  const [pdfFile, setPdfFile] = useState(null)
  const [mode, setMode] = useState('pdf')
  const [editorOpen, setEditorOpen] = useState(false)
  const [editingId, setEditingId] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  async function adminRequest(path, options = {}) {
    return request(path, {
      ...options,
      headers: { ...(options.headers || {}), 'X-Source-Admin-Token': normalizeAdminToken(token) },
    })
  }

  async function refreshAdminSources() {
    setAdminSources(await adminRequest('/api/admin/sources'))
  }

  async function unlock(event) {
    event.preventDefault()
    if (!normalizeAdminToken(token)) return
    setBusy(true)
    setError('')
    try {
      await refreshAdminSources()
      setUnlocked(true)
    } catch (requestError) { setError(requestError.message) }
    finally { setBusy(false) }
  }

  function resetEditor() {
    setEditorOpen(false)
    setEditingId('')
    setForm(emptySourceForm)
    setPdfFile(null)
    setSourceUrl('')
    setError('')
    setNotice('')
  }

  async function previewPdf(event) {
    event.preventDefault()
    if (!pdfFile) { setError('Choose a PDF file first.'); return }
    const payload = new FormData()
    payload.append('file', pdfFile)
    setBusy(true)
    setError('')
    setNotice('')
    try {
      const preview = await adminRequest('/api/admin/sources/preview-pdf', { method: 'POST', body: payload })
      setForm({ ...emptySourceForm, title: preview.suggestedTitle, content: preview.content, sourceType: preview.sourceType, fileName: preview.fileName, url: preview.url })
      setEditorOpen(true)
      setNotice('Text extracted. Review the metadata and content before saving.')
    } catch (requestError) { setError(requestError.message) }
    finally { setBusy(false) }
  }

  async function previewUrl(event) {
    event.preventDefault()
    setBusy(true)
    setError('')
    setNotice('')
    try {
      const preview = await adminRequest('/api/admin/sources/preview-url', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ url: sourceUrl }),
      })
      setForm({ ...emptySourceForm, title: preview.suggestedTitle, content: preview.content, sourceType: preview.sourceType, fileName: preview.fileName, url: preview.url })
      setEditorOpen(true)
      setNotice('Page text extracted. Review the metadata and content before saving.')
    } catch (requestError) { setError(requestError.message) }
    finally { setBusy(false) }
  }

  function editSource(item) {
    const source = item.document
    setEditingId(source.id)
    setMode(source.sourceType === 'LINK' && source.url ? 'url' : 'pdf')
    setSourceUrl(source.url || '')
    setForm({
      title: source.title, organization: source.organization, documentDate: source.documentDate === 'Undated' ? '' : source.documentDate,
      url: source.url || '', crops: source.crops, category: item.category, cropFilter: item.cropFilter,
      regionFilter: item.regionFilter, content: source.content, sourceType: source.sourceType, fileName: source.fileName || '',
    })
    setEditorOpen(true)
    setError('')
    setNotice('Edit the saved source details or extracted text, then save to refresh its index.')
  }

  async function saveSource(event) {
    event.preventDefault()
    setBusy(true)
    setError('')
    setNotice('')
    try {
      const saved = await adminRequest(editingId ? `/api/admin/sources/${encodeURIComponent(editingId)}` : '/api/admin/sources', {
        method: editingId ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form),
      })
      await Promise.all([refreshAdminSources(), onSaved()])
      setNotice(saved.indexed ? 'Source saved and indexed for chat.' : 'Source saved, but indexing did not finish. Use Retry indexing below.')
      setEditorOpen(false)
      setEditingId('')
      setPdfFile(null)
      setForm(emptySourceForm)
    } catch (requestError) { setError(requestError.message) }
    finally { setBusy(false) }
  }

  async function retryIndex(item) {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await adminRequest(`/api/admin/sources/${encodeURIComponent(item.document.id)}/reindex`, { method: 'POST' })
      await refreshAdminSources()
      setNotice(`"${item.document.title}" is indexed for chat.`)
    } catch (requestError) { setError(requestError.message) }
    finally { setBusy(false) }
  }

  async function deleteSource(item) {
    if (!window.confirm(`Delete "${item.document.title}" and remove its search vectors?`)) return
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await adminRequest(`/api/admin/sources/${encodeURIComponent(item.document.id)}`, { method: 'DELETE' })
      await Promise.all([refreshAdminSources(), onSaved()])
      setNotice('Source and its search vectors were deleted.')
    } catch (requestError) { setError(requestError.message) }
    finally { setBusy(false) }
  }

  if (!unlocked) return <section className="source-manager">
    <div className="manager-heading"><div><span className="mini-eyebrow">ADMIN ACCESS</span><h2>Manage source documents</h2><p>Enter the token from the project root .env file. It is held in this page memory only.</p></div><button className="text-button" onClick={onClose}>Close</button></div>
    <form className="manager-unlock" onSubmit={unlock}><label>Source admin token<input required type="password" autoComplete="off" value={token} onChange={event => setToken(event.target.value)} placeholder="Paste token or full SOURCE_ADMIN_TOKEN line"/></label><button className="button button-dark" disabled={busy}>{busy ? 'Checking...' : 'Unlock manager'} <span aria-hidden="true">-&gt;</span></button></form>
    {error && <div className="error-banner">{error}</div>}
  </section>

  return <section className="source-manager">
    <div className="manager-heading"><div><span className="mini-eyebrow">SOURCE ADMIN</span><h2>Manage source documents</h2><p>Review extracted text before saving. Managed records feed the assistant search index.</p></div><button className="text-button" onClick={onClose}>Close manager</button></div>
    {notice && <div className="manager-notice">{notice}</div>}
    {error && <div className="error-banner">{error}</div>}

    {!editorOpen && <div className="source-import-panel">
      <div className="manager-subheading"><div><span className="mini-eyebrow">ADD A REFERENCE</span><h3>Import and review</h3></div><span>PDF up to 6 MB - approved HTTPS pages</span></div>
      <div className="import-tabs"><button type="button" className={mode === 'pdf' ? 'active' : ''} onClick={() => setMode('pdf')}>Upload PDF</button><button type="button" className={mode === 'url' ? 'active' : ''} onClick={() => setMode('url')}>Import approved URL</button></div>
      {mode === 'pdf' ? <form className="import-controls" onSubmit={previewPdf}><label>PDF document<input type="file" accept="application/pdf,.pdf" onChange={event => setPdfFile(event.target.files?.[0] || null)}/></label><button className="button button-dark" disabled={busy || !pdfFile}>{busy ? 'Extracting...' : 'Extract and preview'} <span aria-hidden="true">-&gt;</span></button></form>
        : <form className="import-controls" onSubmit={previewUrl}><label>HTTPS source page or PDF<input type="url" required value={sourceUrl} onChange={event => setSourceUrl(event.target.value)} placeholder="https://agriculture.example.gov.in/reference"/></label><button className="button button-dark" disabled={busy}>{busy ? 'Fetching...' : 'Fetch and preview'} <span aria-hidden="true">-&gt;</span></button></form>}
      <p className="manager-hint">URL imports are restricted to HTTPS agriculture, education, and government domains. Scanned PDFs need OCR before import.</p>
    </div>}

    {editorOpen && <form className="source-editor" onSubmit={saveSource}>
      <div className="manager-subheading"><div><span className="mini-eyebrow">{editingId ? 'EDIT REFERENCE' : 'REVIEW IMPORT'}</span><h3>{editingId ? 'Update source' : 'Review before saving'}</h3></div><button type="button" className="text-button" onClick={resetEditor}>Discard</button></div>
      <div className="source-editor-grid">
        <label>Title<input required maxLength="200" value={form.title} onChange={event => setForm({ ...form, title: event.target.value })}/></label>
        <label>Organization<input required maxLength="200" value={form.organization} onChange={event => setForm({ ...form, organization: event.target.value })} placeholder="e.g. Tamil Nadu Agricultural University"/></label>
        <label>Publication date<input value={form.documentDate} onChange={event => setForm({ ...form, documentDate: event.target.value })} placeholder="e.g. 2026 or 2026-08-12"/></label>
        <label>Crops shown to users<input value={form.crops} onChange={event => setForm({ ...form, crops: event.target.value })} placeholder="Rice, maize, tomato"/></label>
        <label>Knowledge category<select value={form.category} onChange={event => setForm({ ...form, category: event.target.value })}><option value="crop_guidance">Crop guidance</option><option value="crop_forecast">Crop forecast</option><option value="market_reference">Market reference</option><option value="market_inventory">Market inventory</option></select></label>
        <label>Crop filter<select value={form.cropFilter} onChange={event => setForm({ ...form, cropFilter: event.target.value })}><option value="all">All crops</option><option value="rice">Rice</option><option value="maize">Maize</option><option value="tomato">Tomato</option><option value="market">Market</option></select></label>
        <label>Region filter<select value={form.regionFilter} onChange={event => setForm({ ...form, regionFilter: event.target.value })}><option value="tamil_nadu">Tamil Nadu</option><option value="india">India</option><option value="global">Global</option></select></label>
        <label>Source link<input type="url" value={form.url} onChange={event => setForm({ ...form, url: event.target.value })} placeholder="Optional source link"/></label>
        {form.fileName && <div className="source-file-note">PDF file: {form.fileName}</div>}
      </div>
      <label className="source-content-label">Extracted content <span>{form.content.length.toLocaleString()} characters - edit or remove irrelevant text</span><textarea required minLength="80" maxLength="180000" rows="12" value={form.content} onChange={event => setForm({ ...form, content: event.target.value })}/></label>
      <div className="form-actions"><button type="button" className="text-button" onClick={resetEditor}>Cancel</button><button className="button button-dark" disabled={busy}>{busy ? 'Saving and indexing...' : 'Save source'} <span aria-hidden="true">+</span></button></div>
    </form>}

    <div className="managed-source-list"><div className="manager-subheading"><div><span className="mini-eyebrow">LIBRARY STATUS</span><h3>Saved sources <span>{adminSources.length}</span></h3></div></div>
      {adminSources.map(item => <article className="managed-source-row" key={item.document.id}>
        <div className="managed-source-copy"><strong>{item.document.title}</strong><span>{item.document.organization} - {item.category.replaceAll('_', ' ')} - {item.managedByUser ? (item.document.sourceType === 'UPLOAD' ? 'uploaded PDF' : 'managed link') : 'built-in source'}</span></div>
        <span className={`index-status ${item.indexed ? 'ready' : 'pending'}`}>{item.indexed ? 'Ready for chat' : 'Needs indexing'}</span>
        <div className="managed-source-actions">{item.managedByUser ? <><button type="button" onClick={() => editSource(item)}>Edit</button>{!item.indexed && <button type="button" disabled={busy} onClick={() => retryIndex(item)}>Retry indexing</button>}<button type="button" className="danger-action" disabled={busy} onClick={() => deleteSource(item)}>Delete</button></> : <span>Read only</span>}</div>
      </article>)}
    </div>
  </section>
}
