-- =====================================================
-- SCRIPT 1: VERIFICAR SESIONES CON DURACIONES INCORRECTAS
-- =====================================================
-- Copiar y pegar este bloque completo:

SELECT 
    'Duraciones incorrectas (>1000 horas)' as tipo,
    COUNT(*) as cantidad
FROM tc_geofence_sessions 
WHERE exittime IS NOT NULL 
  AND duration > (1000 * 60 * 60 * 1000)

UNION ALL

SELECT 
    'Duraciones en segundos (sospechosas)' as tipo,
    COUNT(*) as cantidad
FROM tc_geofence_sessions 
WHERE exittime IS NOT NULL 
  AND duration > 0
  AND duration < (exittime - entertime) / 1000

UNION ALL

SELECT 
    'Duraciones 0 milisegundos' as tipo,
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
-- SCRIPT 2: CORREGIR DURACIONES INCORRECTAS
-- =====================================================
-- Copiar y pegar este bloque completo:

UPDATE tc_geofence_sessions 
SET duration = (exittime - entertime)
WHERE exittime IS NOT NULL 
  AND entertime IS NOT NULL
  AND (
    -- Duraciones que parecen estar en segundos en lugar de milisegundos
    (duration > 0 AND duration < (exittime - entertime) / 1000)
    OR
    -- Duraciones extremadamente grandes (más de 1000 horas)
    duration > (1000 * 60 * 60 * 1000)
    OR
    -- Duraciones que no coinciden con el cálculo correcto
    duration != (exittime - entertime)
  );

-- =====================================================
-- SCRIPT 3: ELIMINAR SESIONES CON DURACIÓN 0
-- =====================================================
-- Copiar y pegar este bloque completo:

DELETE FROM tc_geofence_sessions 
WHERE exittime IS NOT NULL 
  AND (exittime - entertime) = 0;

-- =====================================================
-- SCRIPT 4: ELIMINAR SESIONES MÁS DE 30 HORAS
-- =====================================================
-- Copiar y pegar este bloque completo:

DELETE FROM tc_geofence_sessions 
WHERE exittime IS NOT NULL 
  AND (exittime - entertime) > (30 * 60 * 60 * 1000);

-- =====================================================
-- SCRIPT 5: ELIMINAR SESIONES MENOS DE 10 MINUTOS
-- =====================================================
-- Copiar y pegar este bloque completo:

DELETE FROM tc_geofence_sessions 
WHERE exittime IS NOT NULL 
  AND (exittime - entertime) < (10 * 60 * 1000);

-- =====================================================
-- SCRIPT 6: VERIFICAR RESULTADO FINAL
-- =====================================================
-- Copiar y pegar este bloque completo:

SELECT 
    COUNT(*) as total_sesiones_restantes,
    MIN((exittime - entertime) / 1000 / 60) as duracion_minima_minutos,
    MAX((exittime - entertime) / 1000 / 60 / 60) as duracion_maxima_horas,
    AVG((exittime - entertime) / 1000 / 60) as duracion_promedio_minutos,
    COUNT(CASE WHEN duration = (exittime - entertime) THEN 1 END) as duraciones_correctas,
    COUNT(CASE WHEN duration != (exittime - entertime) THEN 1 END) as duraciones_incorrectas
FROM tc_geofence_sessions 
WHERE exittime IS NOT NULL;

-- =====================================================
-- SCRIPT 7: OPCIONAL - SESIONES ABIERTAS ANTIGUAS
-- =====================================================
-- Copiar y pegar este bloque completo SI ES NECESARIO:

-- DELETE FROM tc_geofence_sessions 
-- WHERE exittime IS NULL 
--   AND entertime < (UNIX_TIMESTAMP(NOW() - INTERVAL 7 DAY) * 1000);

-- =====================================================
-- SCRIPT 8: VERIFICAR EJEMPLOS DE DURACIONES CORREGIDAS
-- =====================================================
-- Copiar y pegar este bloque completo para ver ejemplos:

SELECT 
    id,
    deviceid,
    geofenceid,
    FROM_UNIXTIME(entertime/1000) as enter_time,
    FROM_UNIXTIME(exittime/1000) as exit_time,
    duration as duration_ms,
    (duration / 1000 / 60) as duration_minutes,
    (exittime - entertime) as calculated_duration_ms,
    ((exittime - entertime) / 1000 / 60) as calculated_duration_minutes
FROM tc_geofence_sessions 
WHERE exittime IS NOT NULL 
ORDER BY duration DESC 
LIMIT 10;