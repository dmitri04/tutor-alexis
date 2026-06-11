-- ============================================================
-- PLAN NUEVO DE ALEXIS — Enfoque de Pensamiento
-- Reemplaza el plan viejo (desalineado) por las 10 semanas nuevas
-- Ejecutar en localhost:9999/h2-console
--
-- IMPORTANTE: Ya tienes respaldo (PDF + backup BD). Este script:
--   1. Borra el plan viejo de OBJETIVO_ESTUDIO
--   2. Inserta las 10 semanas nuevas (15 jun - 21 ago 2026)
--   3. NO toca lecciones, sesiones ni mensajes (progreso intacto)
-- ============================================================

-- Paso 1: Limpiar el plan viejo
DELETE FROM OBJETIVO_ESTUDIO;

-- Paso 2: Insertar el plan nuevo
-- FASE 1 — Pensar con palabras (semanas 1-3)
INSERT INTO OBJETIVO_ESTUDIO (NUMERO_SEMANA, FASE, NOMBRE, SUBTEMAS, ESTADO, FECHA_INICIO, FECHA_FIN) VALUES
(1, 'FASE 1 — Pensar con palabras', 'Comprensión lectora: la idea detrás del texto',
 'Habilidad: DESCOMPONER. Verbal (60%): idea principal vs secundaria, resumir con palabras propias, partir un texto en partes. Matematico (40%): consolidar planteamiento formal de proporciones desde problemas reales.',
 'pendiente', '2026-06-15', '2026-06-19');

INSERT INTO OBJETIVO_ESTUDIO (NUMERO_SEMANA, FASE, NOMBRE, SUBTEMAS, ESTADO, FECHA_INICIO, FECHA_FIN) VALUES
(2, 'FASE 1 — Pensar con palabras', 'Hecho vs opinion y lectura critica',
 'Habilidad: DETECTAR SUPUESTOS Y TRAMPAS. Verbal (60%): distinguir hecho de opinion, detectar lo que un texto da por sentado. Matematico (40%): trampas numericas, porcentajes enganosos, graficas que mienten.',
 'pendiente', '2026-06-22', '2026-06-26');

INSERT INTO OBJETIVO_ESTUDIO (NUMERO_SEMANA, FASE, NOMBRE, SUBTEMAS, ESTADO, FECHA_INICIO, FECHA_FIN) VALUES
(3, 'FASE 1 — Pensar con palabras', 'Argumentar: defender una idea con razones',
 'Habilidad: RAZONAR EXPLICITO. Verbal (60%): construir un argumento, dar razones, identificar argumentos debiles. Matematico (40%): justificar cada paso de un procedimiento, no solo resolver.',
 'pendiente', '2026-06-29', '2026-07-03');

-- FASE 2 — Pensar con numeros y formas (semanas 4-6)
INSERT INTO OBJETIVO_ESTUDIO (NUMERO_SEMANA, FASE, NOMBRE, SUBTEMAS, ESTADO, FECHA_INICIO, FECHA_FIN) VALUES
(4, 'FASE 2 — Pensar con numeros y formas', 'Geometria: pensar con el espacio',
 'Habilidad: DESCOMPONER. Matematico (60%): perimetros, areas y volumenes desde problemas reales. Verbal (40%): seguir instrucciones complejas, traducir un problema geometrico a la accion.',
 'pendiente', '2026-07-06', '2026-07-10');

INSERT INTO OBJETIVO_ESTUDIO (NUMERO_SEMANA, FASE, NOMBRE, SUBTEMAS, ESTADO, FECHA_INICIO, FECHA_FIN) VALUES
(5, 'FASE 2 — Pensar con numeros y formas', 'Algebra como lenguaje',
 'Habilidad: TRANSFERIR. Matematico (60%): la variable como idea, traducir situaciones a expresiones, ecuaciones de dos pasos y con parentesis. Verbal (40%): traducir entre lenguaje cotidiano y algebraico.',
 'pendiente', '2026-07-13', '2026-07-17');

INSERT INTO OBJETIVO_ESTUDIO (NUMERO_SEMANA, FASE, NOMBRE, SUBTEMAS, ESTADO, FECHA_INICIO, FECHA_FIN) VALUES
(6, 'FASE 2 — Pensar con numeros y formas', 'Logica y razonamiento deductivo',
 'Habilidad: RAZONAR EXPLICITO. Matematico (60%): acertijos logicos, deduccion paso a paso, premisas y conclusion. Verbal (40%): silogismos en lenguaje natural, detectar razonamientos validos e invalidos.',
 'pendiente', '2026-07-20', '2026-07-24');

-- FASE 3 — Pensar integrando todo (semanas 7-10)
INSERT INTO OBJETIVO_ESTUDIO (NUMERO_SEMANA, FASE, NOMBRE, SUBTEMAS, ESTADO, FECHA_INICIO, FECHA_FIN) VALUES
(7, 'FASE 3 — Pensar integrando todo', 'Analisis de textos nivel prepa',
 'Habilidad: DETECTAR SUPUESTOS + TRANSFERIR. Verbal (60%): textos complejos, multiples interpretaciones, intencion del autor, inferencias profundas. Matematico (40%): problemas con informacion incompleta o de mas, decidir que datos importan.',
 'pendiente', '2026-07-27', '2026-07-31');

INSERT INTO OBJETIVO_ESTUDIO (NUMERO_SEMANA, FASE, NOMBRE, SUBTEMAS, ESTADO, FECHA_INICIO, FECHA_FIN) VALUES
(8, 'FASE 3 — Pensar integrando todo', 'Problemas integradores',
 'Habilidad: LAS CUATRO COMBINADAS. Mixto (50/50): problemas que requieren descomponer, razonar, detectar trampas y transferir todo junto. Situaciones reales que mezclan numeros, logica y lectura.',
 'pendiente', '2026-08-03', '2026-08-07');

INSERT INTO OBJETIVO_ESTUDIO (NUMERO_SEMANA, FASE, NOMBRE, SUBTEMAS, ESTADO, FECHA_INICIO, FECHA_FIN) VALUES
(9, 'FASE 3 — Pensar integrando todo', 'Pensar bajo presion y verificar',
 'Habilidad: RAZONAR EXPLICITO + verificacion. Mixto (50/50): problemas con vuelta de tuerca donde la intuicion puede fallar y hay que verificar. Entrena la disciplina de revisar el propio pensamiento.',
 'pendiente', '2026-08-10', '2026-08-14');

INSERT INTO OBJETIVO_ESTUDIO (NUMERO_SEMANA, FASE, NOMBRE, SUBTEMAS, ESTADO, FECHA_INICIO, FECHA_FIN) VALUES
(10, 'FASE 3 — Pensar integrando todo', 'Cierre: aprender a aprender',
 'Habilidad: METACOGNICION (todas). Mixto (50/50): Alexis explica como piensa, ensena conceptos al tutor, enfrenta temas nuevos para demostrar que puede aprender solo. Repaso vivo de todo el verano.',
 'pendiente', '2026-08-17', '2026-08-21');

-- Paso 3: Verificar
SELECT NUMERO_SEMANA, FASE, NOMBRE, ESTADO, FECHA_INICIO, FECHA_FIN
FROM OBJETIVO_ESTUDIO
ORDER BY NUMERO_SEMANA;