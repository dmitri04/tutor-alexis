-- ============================================================
-- MARCA DE INICIO DE NUEVA ETAPA — 10 de junio de 2026
-- ============================================================
-- Este es el punto donde el tutor de Alexis cambió de enfoque:
--   ANTES: tutor de temas, organizado por contenido matematico,
--          plan rigido desalineado, ~90% matematicas.
--   DESPUES: tutor de PENSAMIENTO, organizado por 4 habilidades
--          (descomponer, razonar explicito, detectar supuestos,
--          transferir), balance 60/40 verbal/matematico, plan
--          realineado a la realidad de Alexis.
--
-- Respaldo del estado ANTERIOR: PDF + backup de BD (guardados aparte)
--
-- Para comparar antes/despues en el futuro, esta consulta muestra
-- el progreso a partir de la marca:
-- ============================================================

-- Referencia: lecciones ANTES de la nueva etapa (historico previo)
SELECT 'ANTES (hasta 10 jun)' AS etapa, COUNT(*) AS lecciones,
       ROUND(AVG(NIVEL_COMPRENSION), 1) AS promedio
FROM LECCION_COMPLETADA
WHERE FECHA <= '2026-06-10';

-- Referencia: lecciones DESPUES de la nueva etapa (se llena con el tiempo)
SELECT 'DESPUES (desde 11 jun)' AS etapa, COUNT(*) AS lecciones,
       ROUND(AVG(NIVEL_COMPRENSION), 1) AS promedio
FROM LECCION_COMPLETADA
WHERE FECHA >= '2026-06-11';

-- Distribucion verbal vs matematico DESPUES (para verificar el balance 60/40)
-- (cuenta lecciones cuyo tema menciona temas verbales)
SELECT
  SUM(CASE WHEN LOWER(TEMA) LIKE '%lectora%' OR LOWER(TEMA) LIKE '%verbal%'
        OR LOWER(TEMA) LIKE '%texto%' OR LOWER(TEMA) LIKE '%argument%'
        OR LOWER(TEMA) LIKE '%idea%' OR LOWER(TEMA) LIKE '%opinion%'
       THEN 1 ELSE 0 END) AS verbal,
  SUM(CASE WHEN LOWER(TEMA) LIKE '%lectora%' OR LOWER(TEMA) LIKE '%verbal%'
        OR LOWER(TEMA) LIKE '%texto%' OR LOWER(TEMA) LIKE '%argument%'
        OR LOWER(TEMA) LIKE '%idea%' OR LOWER(TEMA) LIKE '%opinion%'
       THEN 0 ELSE 1 END) AS matematico
FROM LECCION_COMPLETADA
WHERE FECHA >= '2026-06-11';
