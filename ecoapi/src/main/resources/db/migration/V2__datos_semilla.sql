-- ============================================================================
-- V2: DATOS SEMILLA EXIGIDOS EN LA SECCION 10 DEL EXAMEN (ANEXO)
-- - 3 roles base (OPERARIO, SUPERVISOR, ADMINISTRADOR) + AUDITOR
-- - Usuarios de prueba con clave cifrada BCrypt para 'Eco2026*'
-- - 12 puntos de acopio distribuidos en 3 zonas (Norte, Centro, Sur)
-- - 2 rutas: Ruta del dia (ABIERTA, 6 puntos, 2 sin pesar para RN-04)
--            y Ruta del dia anterior (CERRADA, 6 puntos, para RN-01)
-- - 15 pesajes: incluye al menos 1 ANULADO, 1 de otro operario (RN-02)
--               y pesajes en ruta cerrada (RN-01).
-- ============================================================================

INSERT INTO roles (nombre, descripcion) VALUES ('OPERARIO', 'Operario de ruta: recorre la ruta del dia y registra pesajes');
INSERT INTO roles (nombre, descripcion) VALUES ('SUPERVISOR', 'Supervisor de operaciones: corrige y anula pesajes, y cierra la ruta');
INSERT INTO roles (nombre, descripcion) VALUES ('ADMINISTRADOR', 'Administrador: gestiona usuarios, roles y padron de puntos de acopio');
INSERT INTO roles (nombre, descripcion) VALUES ('AUDITOR', 'Auditor: consulta rutas, puntos de acopio y pesajes (solo lectura)');

-- Usuarios de prueba (clave en claro: Eco2026*)
INSERT INTO usuarios (username, password, nombre_completo, estado, rol_id)
SELECT 'operario1', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', 'Juan Perez (Operario Ruta 1)', TRUE, id FROM roles WHERE nombre = 'OPERARIO';

INSERT INTO usuarios (username, password, nombre_completo, estado, rol_id)
SELECT 'supervisor1', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', 'Maria Gomez (Supervisora)', TRUE, id FROM roles WHERE nombre = 'SUPERVISOR';

INSERT INTO usuarios (username, password, nombre_completo, estado, rol_id)
SELECT 'admin', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', 'Carlos Admin (Administrador)', TRUE, id FROM roles WHERE nombre = 'ADMINISTRADOR';

INSERT INTO usuarios (username, password, nombre_completo, estado, rol_id)
SELECT 'operario2', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', 'Luis Torres (Operario Turno 2)', TRUE, id FROM roles WHERE nombre = 'OPERARIO';

INSERT INTO usuarios (username, password, nombre_completo, estado, rol_id)
SELECT 'auditor1', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', 'Elena Quispe (Auditora)', TRUE, id FROM roles WHERE nombre = 'AUDITOR';

-- 12 Puntos de Acopio en 3 zonas (Norte, Centro, Sur)
-- Puntos 1 al 6: Ruta del dia (ABIERTA)
INSERT INTO puntos_acopio (razon_social, direccion, zona, material_habitual, secuencia, activo)
VALUES ('Industrias Plasticas del Norte S.A.C.', 'Av. Industrial 450, Parque Norte', 'Norte', 'PET Cristal', 1, TRUE);

INSERT INTO puntos_acopio (razon_social, direccion, zona, material_habitual, secuencia, activo)
VALUES ('Textiles San Martin S.A.', 'Jr. Los Telares 210, Zona Norte', 'Norte', 'Carton Corrugado', 2, TRUE);

INSERT INTO puntos_acopio (razon_social, direccion, zona, material_habitual, secuencia, activo)
VALUES ('Supermercados EcoMarket Peru', 'Av. Arequipa 1280, Cercado', 'Centro', 'Film Stretch', 3, TRUE);

INSERT INTO puntos_acopio (razon_social, direccion, zona, material_habitual, secuencia, activo)
VALUES ('Embotelladora Andina S.A.C.', 'Av. Venezuela 890, Breña', 'Centro', 'Vidrio Verde', 4, TRUE);

INSERT INTO puntos_acopio (razon_social, direccion, zona, material_habitual, secuencia, activo)
VALUES ('Cartonera del Pacifico S.A.', 'Av. El Sol 320, Villa El Salvador', 'Sur', 'Carton Mixto', 5, TRUE);

INSERT INTO puntos_acopio (razon_social, direccion, zona, material_habitual, secuencia, activo)
VALUES ('Centro Logistico Sur S.A.C.', 'Panamericana Sur Km 18.5, Chorrillos', 'Sur', 'Chatarra Ferrosa', 6, TRUE);

-- Puntos 7 al 12: Ruta del dia anterior (CERRADA)
INSERT INTO puntos_acopio (razon_social, direccion, zona, material_habitual, secuencia, activo)
VALUES ('Metalurgica Los Andes S.A.', 'Av. Tupac Amaru 1520, Comas', 'Norte', 'Aluminio', 1, TRUE);

INSERT INTO puntos_acopio (razon_social, direccion, zona, material_habitual, secuencia, activo)
VALUES ('Laboratorios Biofarma S.A.C.', 'Calle Los Sauces 340, Independencia', 'Norte', 'Papel Blanco', 2, TRUE);

INSERT INTO puntos_acopio (razon_social, direccion, zona, material_habitual, secuencia, activo)
VALUES ('Imprenta Grafica Lima S.R.L.', 'Jr. Camana 670, Lima Centro', 'Centro', 'Papel Archivo', 3, TRUE);

INSERT INTO puntos_acopio (razon_social, direccion, zona, material_habitual, secuencia, activo)
VALUES ('Comercializadora Central S.A.', 'Av. Grau 510, La Victoria', 'Centro', 'Carton Corrugado', 4, TRUE);

INSERT INTO puntos_acopio (razon_social, direccion, zona, material_habitual, secuencia, activo)
VALUES ('Agroindustrias del Sur S.A.C.', 'Av. Defensores del Morro 940, Chorrillos', 'Sur', 'HDPE Soplado', 5, TRUE);

INSERT INTO puntos_acopio (razon_social, direccion, zona, material_habitual, secuencia, activo)
VALUES ('Almacenes Unidos del Sur S.A.', 'Av. Pachacutec 2110, Villa Maria', 'Sur', 'Madera Pallets', 6, TRUE);

-- 2 Rutas:
-- Ruta 1: del dia actual, ABIERTA, con 6 puntos (Puntos 1..6)
INSERT INTO rutas_dia (codigo, fecha, zona, estado, operario_username)
VALUES ('RUTA-HOY-01', TRUNC(SYSDATE), 'Multizona', 'ABIERTA', 'operario1');

-- Ruta 2: del dia anterior, CERRADA, con 6 puntos (Puntos 7..12) para demostrar RN-01
INSERT INTO rutas_dia (codigo, fecha, zona, estado, operario_username)
VALUES ('RUTA-AYER-01', TRUNC(SYSDATE) - 1, 'Multizona', 'CERRADA', 'operario1');

-- Asignacion de puntos a Ruta 1 (ABIERTA)
INSERT INTO ruta_puntos (ruta_id, punto_id, secuencia, motivo_omision) VALUES (1, 1, 1, NULL);
INSERT INTO ruta_puntos (ruta_id, punto_id, secuencia, motivo_omision) VALUES (1, 2, 2, NULL);
INSERT INTO ruta_puntos (ruta_id, punto_id, secuencia, motivo_omision) VALUES (1, 3, 3, NULL);
INSERT INTO ruta_puntos (ruta_id, punto_id, secuencia, motivo_omision) VALUES (1, 4, 4, NULL);
INSERT INTO ruta_puntos (ruta_id, punto_id, secuencia, motivo_omision) VALUES (1, 5, 5, NULL);
INSERT INTO ruta_puntos (ruta_id, punto_id, secuencia, motivo_omision) VALUES (1, 6, 6, NULL);

-- Asignacion de puntos a Ruta 2 (CERRADA)
INSERT INTO ruta_puntos (ruta_id, punto_id, secuencia, motivo_omision) VALUES (2, 7, 1, NULL);
INSERT INTO ruta_puntos (ruta_id, punto_id, secuencia, motivo_omision) VALUES (2, 8, 2, NULL);
INSERT INTO ruta_puntos (ruta_id, punto_id, secuencia, motivo_omision) VALUES (2, 9, 3, NULL);
INSERT INTO ruta_puntos (ruta_id, punto_id, secuencia, motivo_omision) VALUES (2, 10, 4, NULL);
INSERT INTO ruta_puntos (ruta_id, punto_id, secuencia, motivo_omision) VALUES (2, 11, 5, NULL);
INSERT INTO ruta_puntos (ruta_id, punto_id, secuencia, motivo_omision) VALUES (2, 12, 6, NULL);

-- 15 Pesajes semilla:
-- 8 en la Ruta 1 (ABIERTA, hoy): Puntos 1, 2, 3 y 4 tienen pesajes; Puntos 5 y 6 quedan SIN PESAR (para demostrar RN-04)
INSERT INTO pesajes (punto_id, ruta_id, fecha_hora, material, peso_bruto, tara, peso_neto, humedad_estimada, observacion, operario, estado, motivo_anulacion)
VALUES (1, 1, SYSTIMESTAMP - INTERVAL '3' HOUR, 'PET Cristal', 125.50, 15.50, 110.00, 4.5, 'Sacos compactados lote A', 'operario1', 'REGISTRADO', NULL);

INSERT INTO pesajes (punto_id, ruta_id, fecha_hora, material, peso_bruto, tara, peso_neto, humedad_estimada, observacion, operario, estado, motivo_anulacion)
VALUES (1, 1, SYSTIMESTAMP - INTERVAL '2' HOUR, 'HDPE Tapas', 48.00, 8.00, 40.00, 2.0, 'Contenedor azul', 'operario1', 'REGISTRADO', NULL);

-- Pesaje ANULADO (exigido por seccion 10)
INSERT INTO pesajes (punto_id, ruta_id, fecha_hora, material, peso_bruto, tara, peso_neto, humedad_estimada, observacion, operario, estado, motivo_anulacion)
VALUES (1, 1, SYSTIMESTAMP - INTERVAL '1' HOUR, 'PET Cristal', 90.00, 10.00, 80.00, 5.0, 'Registro duplicado por error', 'operario1', 'ANULADO', 'Error de lectura en balanza digital');

-- Pesaje de OTRO OPERARIO en la ruta de hoy (para demostrar RN-02: operario1 recibe 403 al intentar editarlo)
INSERT INTO pesajes (punto_id, ruta_id, fecha_hora, material, peso_bruto, tara, peso_neto, humedad_estimada, observacion, operario, estado, motivo_anulacion)
VALUES (2, 1, SYSTIMESTAMP - INTERVAL '2' HOUR, 'Carton Corrugado', 210.00, 20.00, 190.00, 8.5, 'Recogido por apoyo turno mañana', 'operario2', 'REGISTRADO', NULL);

INSERT INTO pesajes (punto_id, ruta_id, fecha_hora, material, peso_bruto, tara, peso_neto, humedad_estimada, observacion, operario, estado, motivo_anulacion)
VALUES (2, 1, SYSTIMESTAMP - INTERVAL '90' MINUTE, 'Papel Mixto', 85.00, 10.00, 75.00, 6.0, 'Fardos atados', 'operario1', 'REGISTRADO', NULL);

INSERT INTO pesajes (punto_id, ruta_id, fecha_hora, material, peso_bruto, tara, peso_neto, humedad_estimada, observacion, operario, estado, motivo_anulacion)
VALUES (3, 1, SYSTIMESTAMP - INTERVAL '60' MINUTE, 'Film Stretch', 64.00, 9.00, 55.00, 3.5, 'Limpiado y seco', 'operario1', 'REGISTRADO', NULL);

INSERT INTO pesajes (punto_id, ruta_id, fecha_hora, material, peso_bruto, tara, peso_neto, humedad_estimada, observacion, operario, estado, motivo_anulacion)
VALUES (3, 1, SYSTIMESTAMP - INTERVAL '45' MINUTE, 'Carton Corrugado', 142.00, 12.00, 130.00, 7.0, 'Cajas de almacen central', 'operario1', 'REGISTRADO', NULL);

INSERT INTO pesajes (punto_id, ruta_id, fecha_hora, material, peso_bruto, tara, peso_neto, humedad_estimada, observacion, operario, estado, motivo_anulacion)
VALUES (4, 1, SYSTIMESTAMP - INTERVAL '30' MINUTE, 'Vidrio Verde', 310.00, 35.00, 275.00, 1.0, 'Jabas plasticas retornables', 'operario1', 'REGISTRADO', NULL);

-- 7 Pesajes en la Ruta 2 (CERRADA, ayer) para completar los 15 pesajes y demostrar RN-01 (409 Conflict en ruta cerrada)
INSERT INTO pesajes (punto_id, ruta_id, fecha_hora, material, peso_bruto, tara, peso_neto, humedad_estimada, observacion, operario, estado, motivo_anulacion)
VALUES (7, 2, SYSTIMESTAMP - INTERVAL '1' DAY, 'Aluminio', 180.00, 15.00, 165.00, 1.5, 'Ruta cerrada dia anterior', 'operario1', 'REGISTRADO', NULL);

INSERT INTO pesajes (punto_id, ruta_id, fecha_hora, material, peso_bruto, tara, peso_neto, humedad_estimada, observacion, operario, estado, motivo_anulacion)
VALUES (8, 2, SYSTIMESTAMP - INTERVAL '1' DAY, 'Papel Blanco', 95.00, 10.00, 85.00, 5.5, 'Ruta cerrada dia anterior', 'operario1', 'REGISTRADO', NULL);

INSERT INTO pesajes (punto_id, ruta_id, fecha_hora, material, peso_bruto, tara, peso_neto, humedad_estimada, observacion, operario, estado, motivo_anulacion)
VALUES (9, 2, SYSTIMESTAMP - INTERVAL '1' DAY, 'Papel Archivo', 150.00, 12.00, 138.00, 6.0, 'Ruta cerrada dia anterior', 'operario1', 'REGISTRADO', NULL);

INSERT INTO pesajes (punto_id, ruta_id, fecha_hora, material, peso_bruto, tara, peso_neto, humedad_estimada, observacion, operario, estado, motivo_anulacion)
VALUES (10, 2, SYSTIMESTAMP - INTERVAL '1' DAY, 'Carton Corrugado', 220.00, 20.00, 200.00, 7.5, 'Ruta cerrada dia anterior', 'operario1', 'REGISTRADO', NULL);

INSERT INTO pesajes (punto_id, ruta_id, fecha_hora, material, peso_bruto, tara, peso_neto, humedad_estimada, observacion, operario, estado, motivo_anulacion)
VALUES (11, 2, SYSTIMESTAMP - INTERVAL '1' DAY, 'HDPE Soplado', 130.00, 15.00, 115.00, 3.0, 'Ruta cerrada dia anterior', 'operario1', 'REGISTRADO', NULL);

INSERT INTO pesajes (punto_id, ruta_id, fecha_hora, material, peso_bruto, tara, peso_neto, humedad_estimada, observacion, operario, estado, motivo_anulacion)
VALUES (12, 2, SYSTIMESTAMP - INTERVAL '1' DAY, 'Madera Pallets', 400.00, 50.00, 350.00, 10.0, 'Ruta cerrada dia anterior', 'operario1', 'REGISTRADO', NULL);

INSERT INTO pesajes (punto_id, ruta_id, fecha_hora, material, peso_bruto, tara, peso_neto, humedad_estimada, observacion, operario, estado, motivo_anulacion)
VALUES (12, 2, SYSTIMESTAMP - INTERVAL '1' DAY, 'Chatarra Ferrosa', 260.00, 25.00, 235.00, 2.0, 'Segundo lote planta sur', 'supervisor1', 'REGISTRADO', NULL);
