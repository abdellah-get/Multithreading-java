/**
 * Les six états d'un thread, un par un.
 *
 * Compiler : javac EtatsDunThread.java
 * Lancer   : java EtatsDunThread
 */
public class EtatsDunThread {

    // Objet qui sert de verrou. wait() et notify() s'appellent sur LUI.
    private static final Object verrou = new Object();

    // Le client le met à true juste avant de s'endormir dans wait().
    private static boolean clientEndormi = false;

    public static void main(String[] args) throws InterruptedException {
        etatNewPuisRunnable();
        etatTimedWaiting();
        etatBlocked();
        etatWaitingPuisTerminated();
    }

    /**
     * NEW : le thread est créé, start() n'a pas été appelé.
     * RUNNABLE : start() a été appelé, il exécute son code.
     */
    static void etatNewPuisRunnable() throws InterruptedException {
        Thread client = new Thread(() -> {
            // Ici le thread tourne vraiment : état RUNNABLE.
            // La boucle ne dort pas et n'appelle pas wait(),
            // donc il reste RUNNABLE le temps que main lise son état.
            long fin = System.nanoTime() + 400_000_000L;
            while (System.nanoTime() < fin) {
                // du calcul, rien d'autre
            }
        });

        // start() n'a pas encore été appelé.
        System.out.println("NEW           -> " + client.getState());

        client.start();

        // 50 ms : le temps qu'il entre dans la boucle.
        Thread.sleep(50);
        System.out.println("RUNNABLE      -> " + client.getState());

        client.join(); // main attend que la boucle soit finie
    }

    /**
     * TIMED_WAITING : le thread attend, avec une durée maximale.
     * sleep, wait(durée) et join(durée) mettent dans cet état.
     */
    static void etatTimedWaiting() throws InterruptedException {
        Thread client = new Thread(() -> {
            try {
                // Pendant cette seconde, l'état est TIMED_WAITING.
                // Au bout de 1000 ms il se réveille tout seul.
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // On remet le drapeau d'interruption, puis on s'arrête.
                Thread.currentThread().interrupt();
            }
        });

        client.start();

        // 100 ms : il est déjà dans sleep, les 1000 ms ne sont pas finies.
        Thread.sleep(100);
        System.out.println("TIMED_WAITING -> " + client.getState());

        client.join();
    }

    /**
     * BLOCKED : le thread veut entrer dans un synchronized,
     * et un autre thread tient déjà le verrou.
     */
    static void etatBlocked() throws InterruptedException {
        Thread client = new Thread(() -> {
            // Si main tient "verrou", le client reste devant cette porte.
            // Pendant cette attente, son état est BLOCKED.
            synchronized (verrou) {
                // Il ne passe cette ligne qu'une fois le verrou obtenu.
            }
        });

        synchronized (verrou) {
            // main tient le verrou AVANT de démarrer le client.
            client.start();

            // 200 ms : le temps que le client arrive au synchronized.
            Thread.sleep(200);
            System.out.println("BLOCKED       -> " + client.getState());
        }
        // En sortant du bloc, main lâche le verrou.
        // Le client peut alors entrer, finir, et devenir TERMINATED.

        client.join();
    }

    /**
     * WAITING : wait() sans durée. Le thread lâche le verrou et s'endort
     * sans limite de temps.
     * Pour sortir : un notify() sur le même objet, puis il reprend le verrou.
     * TERMINATED : run() est terminée.
     */
    static void etatWaitingPuisTerminated() throws InterruptedException {
        Thread client = new Thread(() -> {
            synchronized (verrou) {
                clientEndormi = true;

                // main attend cette nouvelle. On le réveille.
                verrou.notifyAll();

                try {
                    // wait() fait deux choses :
                    // 1. il lâche le verrou, pour que main puisse entrer ;
                    // 2. il passe le client en WAITING, sans limite de temps.
                    verrou.wait();

                    // Au retour de wait(), deux choses sont déjà vraies :
                    // 1. main a appelé notify() ;
                    // 2. le client a repris le verrou.
                    // S'il avait dû attendre le verrou, son état aurait été
                    // BLOCKED pendant cette reprise, puis RUNNABLE ici.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            // Fin de run() : l'état devient TERMINATED.
        });

        synchronized (verrou) {
            client.start();

            // main s'endort tant que le client n'est pas arrivé au wait().
            // Le while revérifie : un réveil ne suffit pas, il faut que
            // clientEndormi soit vrai.
            while (!clientEndormi) {
                verrou.wait();
            }
        }

        // Le client a lâché le verrou en entrant dans wait().
        // On lui laisse un instant, puis on lit son état.
        Thread.sleep(50);
        System.out.println("WAITING       -> " + client.getState());

        synchronized (verrou) {
            // Réveil. Le client ne continue qu'après avoir repris "verrou".
            verrou.notify();
        }

        client.join();
        System.out.println("TERMINATED    -> " + client.getState());
    }
}
