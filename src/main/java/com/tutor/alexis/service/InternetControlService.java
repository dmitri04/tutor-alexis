package com.tutor.alexis.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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