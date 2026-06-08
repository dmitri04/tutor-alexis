-- Limpieza de sesiones vacías
-- Borra sesiones cerradas sin reporte y con menos de 3 mensajes de Alexis
-- Ejecutar desde H2-console: localhost:9999/h2-console
-- Orden importante: primero mensajes, luego sesiones (FK)

DELETE FROM MENSAJE
WHERE SESION_ID IN (
    SELECT ID FROM SESION
    WHERE CIERRE_VOLUNTARIO = TRUE
    AND REPORTE IS NULL
    AND (SELECT COUNT(*) FROM MENSAJE m
         WHERE m.SESION_ID = SESION.ID
         AND m.ROL = 'user') < 3
);

DELETE FROM SESION
WHERE CIERRE_VOLUNTARIO = TRUE
AND REPORTE IS NULL
AND (SELECT COUNT(*) FROM MENSAJE m
     WHERE m.SESION_ID = SESION.ID
     AND m.ROL = 'user') < 3;