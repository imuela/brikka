-- Sprint 32 (contratos/encargo y dossier de viabilidad).
--
-- Análisis previo (informe de cierre del sprint): el catálogo document_types (V2) es una lista
-- cerrada de EJEMPLOS de FUNCTIONAL_SPECIFICATION.md §11, todos documentos que el CLIENTE aporta al
-- broker (nómina, DNI, contrato laboral...). No existe ningún tipo pensado para un documento que el
-- propio broker GENERA a partir de los datos ya existentes en Brikka — este sprint añade
-- exactamente los dos que necesita, sin tocar los diez ya sembrados. EMPLOYMENT_CONTRACT/
-- DEPOSIT_CONTRACT (V2) son documentos aportados por el cliente sobre SU situación laboral o la
-- compraventa; ENGAGEMENT_CONTRACT (el encargo entre broker y cliente) es conceptualmente distinto,
-- de ahí el código nuevo en vez de reutilizar uno existente.
--
-- Arquitectura (decisión documentada en el informe de cierre): en vez de duplicar la infraestructura
-- documental, el dossier y el contrato/encargo SON un Document + DocumentVersion más — mismo modelo,
-- mismo storage (MinIO vía StorageClient), mismo control de acceso (DOCUMENT_READ/DOCUMENT_UPLOAD/
-- DOCUMENT_DOWNLOAD, ya aprobados SUPERADMIN/MANAGER/BROKER), mismo versionado inmutable (cada
-- generación crea una versión nueva, nunca sobrescribe — el snapshot histórico exigido por el sprint
-- es, literalmente, el propio fichero de cada versión). No se necesita una tabla de "contratos"
-- independiente: el contenido generado ya es el snapshot.
INSERT INTO document_types (id, code, name, active) VALUES
    (gen_random_uuid(), 'ENGAGEMENT_CONTRACT', 'Contrato de encargo', true),
    (gen_random_uuid(), 'VIABILITY_DOSSIER', 'Dossier de viabilidad', true);
