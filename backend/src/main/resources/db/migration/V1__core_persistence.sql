CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS source_document (
    id              varchar(120) PRIMARY KEY,
    title           text NOT NULL,
    organization    text NOT NULL,
    document_date   text NOT NULL,
    accessed_date   text NOT NULL,
    url             text NOT NULL,
    crops           text NOT NULL,
    content         text NOT NULL,
    category        varchar(40) NOT NULL,
    crop_filter     varchar(40) NOT NULL,
    region_filter   varchar(80) NOT NULL
);

CREATE INDEX IF NOT EXISTS source_document_category_idx ON source_document (category);
CREATE INDEX IF NOT EXISTS source_document_crop_idx ON source_document (crop_filter);
CREATE INDEX IF NOT EXISTS source_document_region_idx ON source_document (region_filter);

CREATE TABLE IF NOT EXISTS market_listing (
    id              varchar(120) PRIMARY KEY,
    crop            varchar(120) NOT NULL,
    variety         text NOT NULL,
    quantity_kg     numeric(14, 2) NOT NULL CHECK (quantity_kg > 0),
    price_per_kg    numeric(14, 2) NOT NULL CHECK (price_per_kg > 0),
    location        text NOT NULL,
    harvest_date    date NOT NULL,
    available_date  date NOT NULL,
    seller          text NOT NULL,
    status          varchar(24) NOT NULL CHECK (status IN ('AVAILABLE', 'DRAFT')),
    demo_data       boolean NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS market_listing_available_crop_idx
    ON market_listing (lower(crop), status);
CREATE INDEX IF NOT EXISTS market_listing_available_location_idx
    ON market_listing (lower(location), status);

CREATE TABLE IF NOT EXISTS prepared_order (
    id                varchar(120) PRIMARY KEY,
    listing_id        varchar(120) NOT NULL REFERENCES market_listing(id),
    crop              varchar(120) NOT NULL,
    quantity_kg       numeric(14, 2) NOT NULL CHECK (quantity_kg > 0),
    indicative_total  numeric(16, 2) NOT NULL CHECK (indicative_total > 0),
    buyer             text NOT NULL,
    status            varchar(24) NOT NULL CHECK (status = 'PREPARED'),
    note              text NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS prepared_order_listing_idx ON prepared_order (listing_id, created_at DESC);

CREATE TABLE IF NOT EXISTS vector_store (
    id          uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    content     text NOT NULL,
    metadata    json NOT NULL DEFAULT '{}'::json,
    embedding   vector(1024) NOT NULL
);

CREATE TABLE IF NOT EXISTS source_vector_state (
    source_id       varchar(120) NOT NULL REFERENCES source_document(id) ON DELETE CASCADE,
    chunk_index     integer NOT NULL CHECK (chunk_index >= 0),
    vector_id       uuid NOT NULL UNIQUE,
    content_hash    char(64) NOT NULL,
    PRIMARY KEY (source_id, chunk_index)
);

INSERT INTO source_document
    (id, title, organization, document_date, accessed_date, url, crops, content, category, crop_filter, region_filter)
VALUES
    ('tnau-rice-blast', 'Rice blast: field symptoms', 'Tamil Nadu Agricultural University', 'Undated page', '2026-11-10',
     'https://www.agritech.tnau.ac.in/crop_protection/rice_diseases/rice_1.html', 'Rice, paddy',
     $source$TNAU describes rice blast lesions as spindle-shaped spots with a grey centre and dark brown margin. The lesions may coalesce and leaves can dry as disease progresses. The disease can affect leaves, nodes, rachis and glumes. Visual similarity is not a diagnosis; compare crop stage, field pattern and recent conditions with an extension specialist.$source$,
     'crop_guidance', 'rice', 'tamil_nadu'),
    ('tnau-maize-blight', 'Maize leaf blight: field symptoms', 'Tamil Nadu Agricultural University', 'Undated page', '2026-11-10',
     'https://agritech.tnau.ac.in/crop_protection/maize_disease_new/maize_2.html', 'Maize, corn',
     $source$TNAU describes long cigar-shaped grey-green to tan lesions on lower maize leaves. Lesions may lengthen, coalesce and cover the leaf. Wet, humid and cool weather is listed as favourable for this disease. These signs can overlap with other causes of leaf damage.$source$,
     'crop_guidance', 'maize', 'tamil_nadu'),
    ('tnau-tomato-early-blight', 'Tomato early blight: field diagnostic symptoms', 'Tamil Nadu Agricultural University', 'Undated page', '2026-11-10',
     'https://agritech.tnau.ac.in/crop_protection/tomato_diseases_2.html', 'Tomato',
     $source$TNAU describes brown spots with concentric rings in a bull's-eye pattern and a yellow margin. Fruit may be affected near the calyx or stem attachment. The page notes infected crop debris, infected seed and wind- or rain-carried spores as sources of spread. A photo alone cannot establish the cause.$source$,
     'crop_guidance', 'tomato', 'tamil_nadu'),
    ('tnau-march-2026-forecast', 'Pest Forecast Report â€” March 2026', 'Tamil Nadu Agricultural University, Centre for Plant Protection Studies', 'March 2026', '2026-11-10',
     'https://agritech.tnau.ac.in/crop_protection/pdf/2026/04.%20Pest_Forecast_report-March%20%2026.pdf', 'Rice, maize, tomato, pulses, oilseeds and other crops',
     $source$The March 2026 TNAU forecast reports bacterial leaf blight in some Tamil Nadu rice-growing districts and leaf blight incidence in maize-growing areas of Coimbatore and Tiruppur. It also records early blight in tomato-growing districts. These observations are regional and time-bound; do not treat them as a current field diagnosis or a live forecast.$source$,
     'crop_forecast', 'all', 'tamil_nadu'),
    ('agmarknet-sop', 'Daily Data Entry: Standard Operating Procedure', 'Directorate of Marketing & Inspection, Government of India', 'Undated PDF', '2026-11-10',
     'https://www.agmarknet.gov.in/DailyDataEntry/Docs/SOP_DEO.pdf', 'Agricultural market data',
     $source$Agmarknet's data-entry procedure describes recording commodity prices by selected commodity and market, including modal prices. This application does not connect to a live Agmarknet feed; all visible marketplace lots are explicitly marked as prototype sample inventory.$source$,
     'market_reference', 'market', 'india'),
    ('prototype-inventory', 'Marketplace demo inventory', 'Grounded Agri-Market Assistant', 'Updated 2026-11-10', '2026-11-10',
     'http://localhost:8080/api/market/listings', 'Tomato, rice, maize and onion',
     $source$These produce lots are seeded sample entries for interface demonstration. Their quantities and INR/kg figures are not verified market quotations and are not connected to Agmarknet or another live feed.$source$,
     'market_inventory', 'market', 'tamil_nadu')
ON CONFLICT (id) DO NOTHING;

INSERT INTO market_listing
    (id, crop, variety, quantity_kg, price_per_kg, location, harvest_date, available_date, seller, status, demo_data)
VALUES
    ('demo-tomato', 'tomato', 'Hybrid', 420, 28, 'Coimbatore', '2026-11-08', '2026-11-10', 'Farm Co-op 01', 'AVAILABLE', true),
    ('demo-rice', 'rice', 'Ponni', 1200, 31, 'Thanjavur', '2026-11-06', '2026-11-10', 'Delta Growers Group', 'AVAILABLE', true),
    ('demo-maize', 'maize', 'Yellow dent', 800, 24, 'Erode', '2026-11-09', '2026-11-10', 'Green Valley Farm', 'AVAILABLE', true),
    ('demo-onion', 'onion', 'Red onion', 600, 36, 'Dindigul', '2026-11-07', '2026-11-10', 'South Field Produce', 'AVAILABLE', true)
ON CONFLICT (id) DO NOTHING;
