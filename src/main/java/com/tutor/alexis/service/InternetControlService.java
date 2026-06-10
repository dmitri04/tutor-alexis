package com.tutor.alexis.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.util.concurrent.TimeUnit;

@Service
public class InternetControlService {

    @Value("${app.internet.ssh.user}")
    private String sshUser;

    @Value("${app.internet.ssh.host}")
    private String sshHost;

    private volatile String ultimoEstado = "desconocido";

    public boolean bloquear() {
        boolean ok = ejecutar("sudo /usr/local/bin/block_internet.sh");
        if (ok) {
            ultimoEstado = "bloqueado";
            System.out.println("Internet BLOQUEADO en laptop de Alexis (modo estudio)");
        } else {
            System.err.println("ERROR: no se pudo bloquear internet - revisar SSH");
        }
        return ok;
    }

    public boolean desbloquear() {
        boolean ok = ejecutar("sudo /usr/local/bin/unblock_internet.sh");
        if (ok) {
            ultimoEstado = "libre";
            System.out.println("Internet DESBLOQUEADO en laptop de Alexis");
        } else {
            System.err.println("ERROR: no se pudo desbloquear internet - revisar SSH");
        }
        return ok;
    }

    /**
     * Bloqueo asíncrono — no retrasa la respuesta del chat.
     * Se usa al iniciar sesión de estudio.
     */
    public void bloquearAsync() {
        new Thread(this::bloquear, "internet-block").start();
    }

    public String getUltimoEstado() {
        return ultimoEstado;
    }

    /**
     * Health check al arrancar la app — verifica SSH y estado real del iptables.
     */
    @PostConstruct
    public void verificarAlArranque() {
        new Thread(() -> {
            String estado = consultarEstadoReal();
            switch (estado) {
                case "bloqueado" -> {
                    ultimoEstado = "bloqueado";
                    System.out.println("[INTERNET] Conexion SSH OK con " + sshUser + "@" + sshHost
                            + " - estado actual: BLOQUEADO (modo estudio activo)");
                }
                case "libre" -> {
                    ultimoEstado = "libre";
                    System.out.println("[INTERNET] Conexion SSH OK con " + sshUser + "@" + sshHost
                            + " - estado actual: LIBRE (se bloqueara al iniciar sesion de estudio)");
                }
                default -> System.err.println("[INTERNET] SIN conexion SSH con " + sshUser + "@" + sshHost
                        + " - el bloqueo automatico NO funcionara. Verificar laptop encendida y SSH key.");
            }
        }, "internet-healthcheck").start();
    }

    /**
     * Consulta el estado real del firewall en la laptop (no el de memoria).
     * @return "bloqueado", "libre" o "error"
     */
    public String consultarEstadoReal() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ssh",
                    "-o", "BatchMode=yes",
                    "-o", "ConnectTimeout=5",
                    "-o", "StrictHostKeyChecking=no",
                    sshUser + "@" + sshHost,
                    "sudo iptables -S INPUT | head -1"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String salida;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                salida = r.readLine();
            }
            boolean terminado = p.waitFor(10, TimeUnit.SECONDS);
            if (!terminado || p.exitValue() != 0 || salida == null) return "error";
            if (salida.contains("DROP")) return "bloqueado";
            if (salida.contains("ACCEPT")) return "libre";
            return "error";
        } catch (Exception e) {
            return "error";
        }
    }

    private boolean ejecutar(String comando) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ssh",
                    "-o", "BatchMode=yes",          // falla limpio si pide password
                    "-o", "ConnectTimeout=5",
                    "-o", "StrictHostKeyChecking=no",
                    sshUser + "@" + sshHost,
                    comando
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean terminado = p.waitFor(15, TimeUnit.SECONDS);
            if (!terminado) {
                p.destroyForcibly();
                System.err.println("SSH timeout ejecutando: " + comando);
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            System.err.println("Error SSH internet control: " + e.getMessage());
            return false;
        }
    }
}