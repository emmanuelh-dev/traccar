-- =====================================================
-- SCRIPT 1: VERIFICAR SESIONES PROBLEMÁTICAS
-- =====================================================
-- Copiar y pegar este bloque completo:

SELECT 
    'Duración 0 milisegundos' as tipo,
    COUNT(*) as cantidad
FROM tc_geofence_sessions 
WHERE exittime IS NOT NULL 
  AND (exittime - entertime) = 0

UNION ALL

SELECT 
    'Más de 30 horas' as tipo,
    COUNT(*) as cantidad
FROM tc_geofence_sessions 
WHERE exittime IS NOT NULL 
  AND (exittime - entertime) > (30 * 60 * 60 * 1000)

UNION ALL

SELECT 
    'Menos de 10 minutos' as tipo,
    COUNT(*) as cantidad
FROM tc_geofence_sessions 
WHERE exittime IS NOT NULL 
  AND (exittime - entertime) < (10 * 60 * 1000);

-- =====================================================
-- SCRIPT 2: ELIMINAR SESIONES CON DURACIÓN 0
-- =====================================================
-- Copiar y pegar este bloque completo:

DELETE FROM tc_geofence_sessions 
WHERE exittime IS NOT NULL 
  AND (exittime - entertime) = 0;

-- =====================================================
-- SCRIPT 3: ELIMINAR SESIONES MÁS DE 30 HORAS
-- =====================================================
-- Copiar y pegar este bloque completo:

DELETE FROM tc_geofence_sessions 
WHERE exittime IS NOT NULL 
  AND (exittime - entertime) > (30 * 60 * 60 * 1000);

-- =====================================================
-- SCRIPT 4: ELIMINAR SESIONES MENOS DE 10 MINUTOS
-- =====================================================
-- Copiar y pegar este bloque completo:

DELETE FROM tc_geofence_sessions 
WHERE exittime IS NOT NULL 
  AND (exittime - entertime) < (10 * 60 * 1000);

-- =====================================================
-- SCRIPT 5: VERIFICAR RESULTADO FINAL
-- =====================================================
-- Copiar y pegar este bloque completo:

SELECT 
    COUNT(*) as total_sesiones_restantes,
    MIN((exittime - entertime) / 1000 / 60) as duracion_minima_minutos,
    MAX((exittime - entertime) / 1000 / 60 / 60) as duracion_maxima_horas,
    AVG((exittime - entertime) / 1000 / 60) as duracion_promedio_minutos
FROM tc_geofence_sessions 
WHERE exittime IS NOT NULL;

-- =====================================================
-- SCRIPT 6: OPCIONAL - SESIONES ABIERTAS ANTIGUAS
-- =====================================================
-- Copiar y pegar este bloque completo SI ES NECESARIO:

-- DELETE FROM tc_geofence_sessions 
-- WHERE exittime IS NULL 
--   AND entertime < (UNIX_TIMESTAMP(NOW() - INTERVAL 7 DAY) * 1000);