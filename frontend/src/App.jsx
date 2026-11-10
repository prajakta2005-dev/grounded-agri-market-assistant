import { useEffect, useRef, useState } from 'react'
import SourceManager from './SourceManager'

const API = import.meta.env.VITE_API_BASE || 'http://localhost:8080'

async function request(path, options) {
  const response = await fetch(`${API}${path}`, options)
  const payload = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(payload.detail || payload.message || 'Request failed. Please try again.')
  return payload
}

function Icon({ name, size = 18 }) {
  const common = { width: size, height: size, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.7, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': true }
  const paths = {
    home: <><path d="m3 10 9-7 9 7"/><path d="M5 9v11h14V9M9 20v-7h6v7"/></>,
    leaf: <><path d="M20.5 3.5c-8.7 0-14 3.2-14 9.2A6.8 6.8 0 0 0 13.3 19c6 0 7.2-7.7 7.2-15.5Z"/><path d="M3 21c3.5-5 7.4-8.4 13-12"/></>,
    market: <><path d="M3 10h18l-1.5-6h-15L3 10Z"/><path d="M5 10v10h14V10M9 20v-6h6v6M3 10a2.5 2.5 0 0 0 5 0 2.5 2.5 0 0 0 5 0 2.5 2.5 0 0 0 5 0 2.5 2.5 0 0 0 5 0"/></>,
    book: <><path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v17H6.5A2.5 2.5 0 0 0 4 22V5.5Z"/><path d="M4 17a2.5 2.5 0 0 1 2.5-2.5H20M8 7h8M8 10h6"/></>,
    search: <><circle cx="10.8" cy="10.8" r="6.8"/><path d="m16 16 4.5 4.5"/></>,
    arrow: <><path d="M5 12h14M13 6l6 6-6 6"/></>,
    upload: <><path d="M12 16V4m-4 4 4-4 4 4"/><path d="M4 15v5h16v-5"/></>,
    send: <><path d="m21 3-7.5 18-3.3-7.2L3 10.5 21 3Z"/><path d="M10.2 13.8 21 3"/></>,
    pin: <><path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z"/><circle cx="12" cy="10" r="2.5"/></>,
    calendar: <><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18"/></>,
    close: <><path d="m6 6 12 12M18 6 6 18"/></>,
    check: <><path d="m5 12 4 4L19 6"/></>,
    plus: <><path d="M12 5v14M5 12h14"/></>,
    alert: <><path d="M12 3 2.5 20h19L12 3Z"/><path d="M12 9v4m0 3h.01"/></>,
    menu: <><path d="M4 6h16M4 12h16M4 18h16"/></>,
  }
  return <svg {...common}>{paths[name] || paths.leaf}</svg>
}

function SourceCard({ source, index }) {
  const Tag = source.url ? 'a' : 'div'
  return <Tag className="source-card" href={source.url || undefined} target={source.url ? '_blank' : undefined} rel={source.url ? 'noreferrer' : undefined}>
    <div className="source-number">S{index + 1}</div>
    <div className="source-copy">
      <strong>{source.title}</strong>
      <span>{source.organization} · {source.documentDate}</span>
    </div>
    <span className="source-open">{source.url ? '↗' : 'PDF'}</span>
  </Tag>
}

function ListingCard({ listing, onOrder }) {
  return <article className="listing-card">
    <div className="listing-topline"><span className={`crop-token token-${listing.crop.toLowerCase()}`}>{listing.crop.slice(0, 1).toUpperCase()}</span><span className="sample-pill">{listing.demoData ? 'Sample lot' : listing.status}</span></div>
    <h3>{listing.crop}</h3>
    <p className="listing-variety">{listing.variety} <span>·</span> {listing.seller}</p>
    <div className="listing-price">₹{Number(listing.pricePerKg).toFixed(0)}<small> / kg</small></div>
    <div className="listing-meta"><span><Icon name="pin" size={15}/>{listing.location}</span><span><Icon name="calendar" size={15}/>{listing.harvestDate}</span></div>
    <div className="listing-bottom"><span>{Number(listing.quantityKg).toLocaleString()} kg available</span><button className="button button-small" onClick={() => onOrder(listing)}>Prepare order <Icon name="arrow" size={15}/></button></div>
  </article>
}

export default function App() {
  const [view, setView] = useState('overview')
  const [mobileNav, setMobileNav] = useState(false)
  const [question, setQuestion] = useState('')
  const [photo, setPhoto] = useState(null)
  const [photoPreview, setPhotoPreview] = useState('')
  const [messages, setMessages] = useState([])
  const [chatBusy, setChatBusy] = useState(false)
  const [chatError, setChatError] = useState('')
  const [listings, setListings] = useState([])
  const [marketQuery, setMarketQuery] = useState('')
  const [marketLocation, setMarketLocation] = useState('')
  const [marketBusy, setMarketBusy] = useState(false)
  const [marketError, setMarketError] = useState('')
  const [sources, setSources] = useState([])
  const [sourcesError, setSourcesError] = useState('')
  const [showSourceManager, setShowSourceManager] = useState(false)
  const [showListingForm, setShowListingForm] = useState(false)
  const [listingForm, setListingForm] = useState({ crop: '', variety: '', quantityKg: '', pricePerKg: '', location: '', harvestDate: '', seller: '' })
  const [listingBusy, setListingBusy] = useState(false)
  const [draftListing, setDraftListing] = useState(null)
  const [selectedListing, setSelectedListing] = useState(null)
  const [orderQuantity, setOrderQuantity] = useState('')
  const [orderBusy, setOrderBusy] = useState(false)
  const [preparedOrder, setPreparedOrder] = useState(null)
  const fileInput = useRef(null)
  const chatEnd = useRef(null)

  useEffect(() => {
    if (view === 'market') loadListings()
    if (view === 'sources' || view === 'overview') loadSources()
  }, [view])

  useEffect(() => { chatEnd.current?.scrollIntoView({ behavior: 'smooth', block: 'end' }) }, [messages])

  async function loadListings(query = marketQuery, location = marketLocation) {
    setMarketBusy(true)
    setMarketError('')
    try {
      const params = new URLSearchParams({ query, location })
      const data = await request(`/api/market/listings?${params}`)
      setListings(data)
    } catch (error) { setMarketError(error.message) }
    finally { setMarketBusy(false) }
  }

  async function loadSources() {
    try { setSources(await request('/api/sources')) }
    catch (error) { setSourcesError(error.message) }
  }

  function choosePhoto(file) {
    if (!file) return
    setPhoto(file)
    setPhotoPreview(URL.createObjectURL(file))
  }

  async function sendMessage(event) {
    event?.preventDefault()
    if ((!question.trim() && !photo) || chatBusy) return
    const currentQuestion = question.trim()
    const currentPhoto = photo
    const userMessage = { role: 'user', text: currentQuestion || 'Please check this crop photo.', image: photoPreview }
    setMessages(previous => [...previous, userMessage])
    setQuestion('')
    setPhoto(null)
    setPhotoPreview('')
    setChatError('')
    setChatBusy(true)
    const form = new FormData()
    form.append('question', currentQuestion)
    if (currentPhoto) form.append('photo', currentPhoto)
    try {
      const answer = await request('/api/assistant/chat', { method: 'POST', body: form })
      setMessages(previous => [...previous, { role: 'assistant', ...answer }])
    } catch (error) {
      setChatError(error.message)
      setMessages(previous => [...previous, { role: 'assistant', answer: `I couldn't complete that request. ${error.message}`, sources: [], error: true }])
    } finally { setChatBusy(false) }
  }

  async function prepareListing(event) {
    event.preventDefault()
    setListingBusy(true)
    setMarketError('')
    try {
      const payload = await request('/api/market/listings', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({
          ...listingForm, quantityKg: Number(listingForm.quantityKg), pricePerKg: Number(listingForm.pricePerKg),
        }),
      })
      setDraftListing(payload)
      setListingForm({ crop: '', variety: '', quantityKg: '', pricePerKg: '', location: '', harvestDate: '', seller: '' })
      setShowListingForm(false)
    } catch (error) { setMarketError(error.message) }
    finally { setListingBusy(false) }
  }

  async function prepareOrder(event) {
    event.preventDefault()
    if (!selectedListing) return
    setOrderBusy(true)
    setMarketError('')
    try {
      const payload = await request('/api/market/orders', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ listingId: selectedListing.id, quantityKg: Number(orderQuantity) }),
      })
      setPreparedOrder(payload)
      setSelectedListing(null)
      setOrderQuantity('')
    } catch (error) { setMarketError(error.message) }
    finally { setOrderBusy(false) }
  }

  const navItems = [
    { id: 'overview', label: 'Overview', icon: 'home' },
    { id: 'advisor', label: 'Crop advisor', icon: 'leaf' },
    { id: 'market', label: 'Marketplace', icon: 'market' },
    { id: 'sources', label: 'Source library', icon: 'book' },
  ]
  const titles = { overview: 'Farm workspace', advisor: 'Crop advisor', market: 'Produce marketplace', sources: 'Source library' }

  return <div className="app-shell">
    <aside className={`sidebar ${mobileNav ? 'sidebar-open' : ''}`}>
      <a className="brand" href="#overview" onClick={() => setView('overview')}><span className="brand-mark"><Icon name="leaf" size={21}/></span><span>fieldnote<small>AGRI-MARKET ASSISTANT</small></span></a>
      <div className="workspace-label">WORKSPACE</div>
      <nav className="side-nav" aria-label="Main navigation">
        {navItems.map(item => <button key={item.id} className={`nav-item ${view === item.id ? 'active' : ''}`} onClick={() => { setView(item.id); setMobileNav(false) }}><Icon name={item.icon}/><span>{item.label}</span>{item.id === 'sources' && sources.length > 0 && <i className="nav-count">{sources.length}</i>}</button>)}
      </nav>
      <div className="sidebar-spacer"/>
      <div className="sidebar-note"><div className="note-icon"><Icon name="book" size={17}/></div><strong>Grounded answers</strong><p>Advice is tied to dated documents, with uncertainty called out.</p><button onClick={() => setView('sources')}>Browse sources <Icon name="arrow" size={14}/></button></div>
      <div className="profile-row"><div className="avatar">F</div><div><strong>Farm workspace</strong></div><span className="online-dot"/></div>
    </aside>

    <main className="main-area">
      <header className="topbar"><button className="mobile-menu icon-button" onClick={() => setMobileNav(!mobileNav)} aria-label="Toggle navigation"><Icon name="menu"/></button><div className="breadcrumbs">Workspace <span>/</span> <strong>{titles[view]}</strong></div><div className="topbar-right"><span className="today-date"><Icon name="calendar" size={15}/>{new Intl.DateTimeFormat('en-IN', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' }).format(new Date())}</span><span className="top-divider"/><button className="help-link" onClick={() => setView('sources')}>How sources work <span>↗</span></button></div></header>
      <div className="page-content">
        {view === 'overview' && <>
          <section className="welcome-row"><div><div className="eyebrow"><span className="eyebrow-dot"/> FIELD DESK <span className="eyebrow-line"/> SEASONAL NOTES, SOURCED</div><h1>Grow with <em>confidence.</em></h1><p className="welcome-sub">Crop guidance and produce discovery, grounded in the sources behind each answer.</p></div><button className="button button-dark" onClick={() => setView('advisor')}><Icon name="leaf" size={16}/> Ask the crop advisor</button></section>
          <section className="hero-grid">
            <article className="advisor-card">
              <div className="card-orbit orbit-one"/><div className="card-orbit orbit-two"/><div className="advisor-card-content">
                <div className="card-kicker"><span className="live-dot"/> FIELDNOTE ASSISTANT <span className="vision-tag">TEXT + PHOTO</span></div>
                <h2>What’s happening<br/>in your field?</h2>
                <p>Ask about symptoms, crop care, or local produce. Every answer comes with its source trail.</p>
                <form className="quick-ask" onSubmit={event => { setView('advisor'); sendMessage(event) }}><Icon name="search" size={18}/><input value={question} onChange={event => setQuestion(event.target.value)} placeholder="Try “What causes spots on rice leaves?”"/><button aria-label="Ask" type="submit"><Icon name="arrow"/></button></form>
                <div className="suggestion-row"><span>Popular</span><button onClick={() => { setQuestion('How can I identify rice blast symptoms?'); setView('advisor') }}>Rice leaf spots</button><button onClick={() => { setQuestion('Search available produce in Coimbatore'); setView('advisor') }}>Market nearby</button></div>
              </div>
              <div className="field-art" aria-hidden="true"><div className="sun"/><div className="hill hill-back"/><div className="hill hill-front"/><div className="furrow f1"/><div className="furrow f2"/><div className="furrow f3"/><div className="plant plant-a"><i/><b/><em/></div><div className="plant plant-b"><i/><b/><em/></div><div className="plant plant-c"><i/><b/><em/></div></div>
            </article>
            <aside className="photo-card"><div className="photo-illustration"><div className="photo-sky"/><div className="photo-hill"/><div className="leaf-shape leaf-a"/><div className="leaf-shape leaf-b"/><div className="leaf-shape leaf-c"/><div className="photo-frame"><Icon name="upload" size={20}/></div></div><div className="photo-card-copy"><span className="mini-eyebrow">PHOTO TRIAGE</span><h3>See a symptom?<br/>Share a photo.</h3><p>Get a first look, then compare against trusted crop guidance.</p><button onClick={() => { setView('advisor'); setTimeout(() => fileInput.current?.click(), 80) }}>Upload crop photo <Icon name="arrow" size={15}/></button></div></aside>
          </section>
          <section className="section-heading"><div><span className="mini-eyebrow">START HERE</span><h2>Your field tools</h2></div><span className="subtle-caption">One workspace, practical next steps</span></section>
          <section className="tool-grid">
            <button className="tool-card" onClick={() => setView('advisor')}><span className="tool-icon tool-icon-green"><Icon name="leaf" size={20}/></span><span className="tool-copy"><strong>Crop health check</strong><small>Share symptoms or a field photo</small></span><Icon name="arrow" size={17}/></button>
            <button className="tool-card" onClick={() => setView('market')}><span className="tool-icon tool-icon-yellow"><Icon name="market" size={20}/></span><span className="tool-copy"><strong>Find produce</strong><small>Explore available sample lots</small></span><Icon name="arrow" size={17}/></button>
            <button className="tool-card" onClick={() => setView('sources')}><span className="tool-icon tool-icon-blue"><Icon name="book" size={20}/></span><span className="tool-copy"><strong>Read the sources</strong><small>Open dated notes and references</small></span><Icon name="arrow" size={17}/></button>
          </section>
          <section className="below-grid"><div className="section-heading compact"><div><span className="mini-eyebrow">RECENT REFERENCES</span><h2>In the field library</h2></div><button className="text-button" onClick={() => setView('sources')}>View all <Icon name="arrow" size={15}/></button></div><div className="source-list compact-list">{sources.slice(0, 3).map((source, index) => <SourceCard key={source.id} source={source} index={index}/>)}{sourcesError && <div className="inline-error">{sourcesError}</div>}</div></section>
        </>}

        {view === 'advisor' && <section className="advisor-view">
          <div className="view-heading"><div><div className="eyebrow"><span className="eyebrow-dot"/> CROP SUPPORT</div><h1>Crop <em>advisor</em></h1><p>Ask a crop question or attach a photo for symptom triage.</p></div><div className="trust-chip"><Icon name="book" size={16}/> Source-linked responses</div></div>
          <div className="advisor-layout"><div className="chat-panel">
            <div className="chat-top"><div className="assistant-avatar"><Icon name="leaf" size={19}/></div><div><strong>Fieldnote assistant</strong><span><i className="online-dot"/> Grounded in retrieved documents</span></div><span className="chat-mode">NEMOTRON VISION</span></div>
            <div className="chat-messages">
              {messages.length === 0 && <div className="empty-chat"><div className="empty-illustration"><span>✳</span><Icon name="leaf" size={30}/></div><h2>Let’s look closer.</h2><p>Tell me what you’re seeing, which crop and stage it’s at, and where the field is. A clear photo can help with an initial symptom check.</p><div className="prompt-suggestions"><button onClick={() => setQuestion('What do grey-centered spots on rice leaves mean?')}>Grey spots on rice leaves <Icon name="arrow" size={14}/></button><button onClick={() => setQuestion('What details should I collect before asking an agronomist?')}>Prepare for an expert visit <Icon name="arrow" size={14}/></button></div><div className="uncertainty-note"><Icon name="alert" size={16}/> Photo triage is a starting point, not a confirmed diagnosis.</div></div>}
              {messages.map((message, index) => <div key={index} className={`message-row ${message.role}`}>
                {message.role === 'assistant' && <div className="assistant-avatar tiny"><Icon name="leaf" size={15}/></div>}
                <div className="message-bubble">{message.image && <img className="message-image" src={message.image} alt="Uploaded crop"/>}{message.role === 'user' ? <p>{message.text}</p> : <><p className="answer-text">{message.answer}</p>{message.sources?.length > 0 && <div className="message-sources"><span className="mini-eyebrow">SOURCES USED</span>{message.sources.map((source, sourceIndex) => <SourceCard key={source.id} source={source} index={sourceIndex}/>)}</div>}</>}</div>
              </div>)}
              {chatBusy && <div className="message-row assistant"><div className="assistant-avatar tiny"><Icon name="leaf" size={15}/></div><div className="typing-bubble"><span/><span/><span/><em>Checking sources…</em></div></div>}
              <div ref={chatEnd}/>
            </div>
            {chatError && <div className="error-banner"><Icon name="alert" size={16}/>{chatError}</div>}
            <form className="chat-composer" onSubmit={sendMessage}>
              {photoPreview && <div className="photo-attachment"><img src={photoPreview} alt="Crop photo preview"/><span>{photo?.name}</span><button type="button" onClick={() => { setPhoto(null); setPhotoPreview('') }} aria-label="Remove photo"><Icon name="close" size={15}/></button></div>}
              <div className="composer-line"><button type="button" className="attach-button" onClick={() => fileInput.current?.click()} title="Attach crop photo"><Icon name="upload" size={19}/></button><input ref={fileInput} type="file" accept="image/jpeg,image/png,image/webp" onChange={event => choosePhoto(event.target.files?.[0])} hidden/><textarea value={question} onChange={event => setQuestion(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); sendMessage() } }} placeholder="Ask about symptoms, crop care, or markets…" rows="1"/><button type="submit" disabled={chatBusy || (!question.trim() && !photo)} className="send-button" aria-label="Send"><Icon name="send" size={17}/></button></div>
              <div className="composer-foot"><span><Icon name="alert" size={13}/> Confirm diagnosis and treatments with a qualified local adviser.</span><span>Enter to send · Shift + Enter for a new line</span></div>
            </form>
          </div>
          <aside className="advisor-aside"><div className="aside-card"><span className="mini-eyebrow">GOOD PHOTO, BETTER CLUES</span><h3>Help the advisor see the whole picture.</h3><ul className="photo-tips"><li><span>01</span> Photograph the affected area in daylight.</li><li><span>02</span> Include a close-up and the whole plant.</li><li><span>03</span> Add the crop stage and recent weather.</li></ul></div><div className="aside-card aside-green"><div className="aside-icon"><Icon name="book" size={18}/></div><h3>Answers carry their sources.</h3><p>Advice includes links and document dates. Undated references are labelled clearly.</p><button className="text-button" onClick={() => setView('sources')}>Explore the library <Icon name="arrow" size={15}/></button></div><div className="aside-date"><Icon name="calendar" size={15}/> Source library accessed Nov 10, 2026</div></aside></div>
        </section>}

        {view === 'market' && <section className="market-view">
          <div className="view-heading"><div><div className="eyebrow"><span className="eyebrow-dot"/> CONNECT TO YOUR NEXT HARVEST</div><h1>Produce <em>marketplace</em></h1><p>Search local sample lots, prepare an order, or draft your own listing.</p></div><button className="button button-dark" onClick={() => { setShowListingForm(!showListingForm); setPreparedOrder(null) }}><Icon name="plus" size={17}/>{showListingForm ? 'Close listing form' : 'Prepare a listing'}</button></div>
          <div className="market-notice"><span className="notice-mark">i</span><span><strong>Prototype inventory.</strong> Lots and prices below are sample data for this demo, not live market quotations.</span><button type="button" onClick={() => setView('sources')}>Data note</button></div>
          {preparedOrder && <div className="success-banner"><span className="success-icon"><Icon name="check" size={17}/></span><div><strong>Order draft ready · {preparedOrder.id}</strong><span>{preparedOrder.quantityKg} kg of {preparedOrder.crop} · indicative total ₹{Number(preparedOrder.indicativeTotal).toLocaleString('en-IN')}</span></div><button onClick={() => setPreparedOrder(null)} aria-label="Dismiss"><Icon name="close" size={16}/></button></div>}
          {draftListing && <div className="success-banner"><span className="success-icon"><Icon name="check" size={17}/></span><div><strong>Listing draft prepared · {draftListing.id}</strong><span>{draftListing.crop} · {draftListing.quantityKg} kg · review before publishing</span></div><button onClick={() => setDraftListing(null)} aria-label="Dismiss"><Icon name="close" size={16}/></button></div>}
          {showListingForm && <form className="listing-form" onSubmit={prepareListing}><div className="form-title"><div><span className="mini-eyebrow">NEW DRAFT</span><h2>List your produce</h2></div><p>This saves a review draft only. It won’t be published.</p></div><div className="form-grid"><label>Crop<input required value={listingForm.crop} onChange={e => setListingForm({ ...listingForm, crop: e.target.value })} placeholder="e.g. Tomato"/></label><label>Variety<input value={listingForm.variety} onChange={e => setListingForm({ ...listingForm, variety: e.target.value })} placeholder="e.g. Hybrid"/></label><label>Quantity (kg)<input required type="number" min="1" step="1" value={listingForm.quantityKg} onChange={e => setListingForm({ ...listingForm, quantityKg: e.target.value })} placeholder="0"/></label><label>Asking price / kg (₹)<input required type="number" min="0.01" step="0.01" value={listingForm.pricePerKg} onChange={e => setListingForm({ ...listingForm, pricePerKg: e.target.value })} placeholder="0.00"/></label><label>Town or district<input required value={listingForm.location} onChange={e => setListingForm({ ...listingForm, location: e.target.value })} placeholder="e.g. Coimbatore"/></label><label>Harvest date<input required type="date" value={listingForm.harvestDate} onChange={e => setListingForm({ ...listingForm, harvestDate: e.target.value })}/></label></div><div className="form-actions"><button className="button button-dark" disabled={listingBusy}>{listingBusy ? 'Preparing…' : 'Save listing draft'} <Icon name="arrow" size={15}/></button></div></form>}
          <form className="market-search" onSubmit={event => { event.preventDefault(); loadListings() }}><div className="market-search-field"><Icon name="search" size={19}/><input value={marketQuery} onChange={e => setMarketQuery(e.target.value)} placeholder="Search crop or variety"/><span className="search-key">⌘ K</span></div><div className="market-search-field location-field"><Icon name="pin" size={18}/><input value={marketLocation} onChange={e => setMarketLocation(e.target.value)} placeholder="Town or district"/></div><button className="button button-dark" disabled={marketBusy}>{marketBusy ? 'Searching…' : 'Search lots'} <Icon name="arrow" size={15}/></button></form>
          {marketError && <div className="error-banner"><Icon name="alert" size={16}/>{marketError}</div>}
          <div className="market-results-head"><div><h2>Available produce <span>{listings.length}</span></h2><p>Sample listings · updated Nov 10, 2026</p></div><button className="sort-select" type="button">Nearest harvest <span>⌄</span></button></div>
          {marketBusy ? <div className="loading-state">Loading sample lots…</div> : <div className="listing-grid">{listings.map(listing => <ListingCard key={listing.id} listing={listing} onOrder={item => { setSelectedListing(item); setOrderQuantity(''); setPreparedOrder(null) }}/>)}{listings.length === 0 && <div className="empty-results"><div className="empty-illustration"><Icon name="search" size={25}/></div><h3>No matching lots yet</h3><p>Try another crop or location.</p></div>}</div>}
          <div className="market-source-row"><Icon name="book" size={17}/><span>Data transparency</span><p>Official market data is not connected in this prototype.</p><button className="text-button" onClick={() => setView('sources')}>About the sources <Icon name="arrow" size={14}/></button></div>
        </section>}

        {view === 'sources' && <section className="sources-view">
          <div className="view-heading"><div><div className="eyebrow"><span className="eyebrow-dot"/> FIELD LIBRARY</div><h1>Evidence, <em>in reach.</em></h1><p>Documents used by the assistant, with publication and access dates where available.</p></div><span className="source-count-pill"><Icon name="book" size={16}/>{sources.length} references</span></div>
          <div className="source-intro"><div className="source-intro-icon"><Icon name="book" size={22}/></div><div><strong>How grounded answers work</strong><p>The assistant retrieves matching references for each question and links them beside its response. Undated pages are marked as undated; regional or older recommendations may not reflect current local conditions.</p></div></div>
          <div className="manager-open-row"><button className="button button-dark" onClick={() => setShowSourceManager(!showSourceManager)}><Icon name="plus" size={15}/>{showSourceManager ? 'Hide source manager' : 'Manage sources'}</button></div>
          {showSourceManager && <SourceManager onClose={() => setShowSourceManager(false)} onSaved={loadSources}/>}
          {sourcesError && <div className="error-banner"><Icon name="alert" size={16}/>{sourcesError}</div>}
          <div className="source-library-list">{sources.map((source, index) => <article className="library-row" key={source.id}><span className="library-index">{String(index + 1).padStart(2, '0')}</span><div className="library-body"><div className="library-tags"><span>{source.crops}</span><span>{source.documentDate}</span>{source.sourceType === 'UPLOAD' && <span>Uploaded PDF</span>}</div><h2>{source.title}</h2><p>{source.content}</p><div className="library-foot"><span>{source.organization} | Accessed {source.accessedDate}{source.fileName ? ` | ${source.fileName}` : ''}</span>{source.url ? <a href={source.url} target="_blank" rel="noreferrer">Open original source</a> : <span>Text extracted from uploaded document</span>}</div></div></article>)}</div>
          <div className="access-note"><Icon name="calendar" size={16}/> Library references reviewed Nov 10, 2026. Undated pages are not assigned a publication date.</div>
        </section>}
      </div>
    </main>

    {mobileNav && <button className="mobile-backdrop" onClick={() => setMobileNav(false)} aria-label="Close navigation"/>}
    {selectedListing && <div className="modal-backdrop" role="presentation" onClick={() => setSelectedListing(null)}><form className="order-modal" onSubmit={prepareOrder} onClick={event => event.stopPropagation()}><button type="button" className="modal-close icon-button" onClick={() => setSelectedListing(null)} aria-label="Close"><Icon name="close"/></button><span className="mini-eyebrow">ORDER DRAFT</span><h2>Prepare an order</h2><p className="modal-item">{selectedListing.crop} · {selectedListing.variety}<br/><span>{selectedListing.quantityKg} kg available · ₹{selectedListing.pricePerKg}/kg · {selectedListing.location}</span></p><label>Quantity in kilograms<input autoFocus type="number" min="1" max={selectedListing.quantityKg} step="1" required value={orderQuantity} onChange={event => setOrderQuantity(event.target.value)}/></label><div className="order-total">Indicative total <strong>₹{(Number(orderQuantity || 0) * Number(selectedListing.pricePerKg)).toLocaleString('en-IN')}</strong></div><p className="modal-hint">This only prepares a draft. Confirm the final price and availability with the seller.</p><button className="button button-dark full-button" disabled={orderBusy}>{orderBusy ? 'Preparing…' : 'Prepare order draft'} <Icon name="arrow" size={15}/></button></form></div>}
  </div>
}
