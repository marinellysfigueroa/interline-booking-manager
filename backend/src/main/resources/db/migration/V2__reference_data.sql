-- =============================================================================
-- V2: datos de referencia de DEMOSTRACIÓN.
-- Los acuerdos interline son FICTICIOS y solo sirven para ilustrar la regla de
-- ticket único; no reflejan acuerdos comerciales reales.
-- =============================================================================

INSERT INTO airport (code, name, city, country_code, time_zone, search_key) VALUES
    ('BOG', 'El Dorado International',            'Bogotá',       'CO', 'America/Bogota',      'bog bogota el dorado international colombia'),
    ('MDE', 'José María Córdova International',   'Medellín',     'CO', 'America/Bogota',      'mde medellin jose maria cordova international rionegro colombia'),
    ('CTG', 'Rafael Núñez International',         'Cartagena',    'CO', 'America/Bogota',      'ctg cartagena rafael nunez international colombia'),
    ('MAD', 'Adolfo Suárez Madrid-Barajas',       'Madrid',       'ES', 'Europe/Madrid',       'mad madrid adolfo suarez barajas espana spain'),
    ('BCN', 'Josep Tarradellas Barcelona-El Prat','Barcelona',    'ES', 'Europe/Madrid',       'bcn barcelona josep tarradellas el prat espana spain'),
    ('FCO', 'Leonardo da Vinci–Fiumicino',        'Roma',         'IT', 'Europe/Rome',         'fco roma rome leonardo da vinci fiumicino italia italy'),
    ('MIA', 'Miami International',                'Miami',        'US', 'America/New_York',    'mia miami international estados unidos united states'),
    ('JFK', 'John F. Kennedy International',      'Nueva York',   'US', 'America/New_York',    'jfk nueva york new york john f kennedy international estados unidos'),
    ('LIM', 'Jorge Chávez International',         'Lima',         'PE', 'America/Lima',        'lim lima jorge chavez international peru'),
    ('SCL', 'Arturo Merino Benítez International','Santiago',     'CL', 'America/Santiago',    'scl santiago arturo merino benitez international chile');

INSERT INTO airline (code, name, accounting_code, loyalty_program) VALUES
    ('AV', 'Avianca',     '134', 'lifemiles'),
    ('IB', 'Iberia',      '075', 'Iberia Club'),
    ('AZ', 'ITA Airways', '055', 'Volare'),
    ('UX', 'Air Europa',  '996', 'SUMA'),
    ('AA', 'American Airlines', '001', 'AAdvantage'),
    ('LA', 'LATAM Airlines',    '045', 'LATAM Pass');

INSERT INTO airline_interline_partner (airline_code, partner_code) VALUES
    ('AV', 'IB'), ('AV', 'AZ'), ('AV', 'UX'),
    ('IB', 'AV'), ('IB', 'AA'), ('IB', 'LA'), ('IB', 'AZ'),
    ('AZ', 'AV'), ('AZ', 'IB'),
    ('UX', 'AV'),
    ('AA', 'IB'), ('AA', 'LA'),
    ('LA', 'AA'), ('LA', 'IB');
