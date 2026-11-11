package booking.examples;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 1. volatile suffit pour UNE lecture ou UNE écriture (la visibilité).
 * 2. volatile ne suffit pas pour ++ (l'atomicité).
 * 3. synchronized ou AtomicInteger réparent ++.
 *
 * Compiler : javac VolatileEtAtomique.java
 * Lancer   : java VolatileEtAtomique
 */
public class VolatileEtAtomique {

    // volatile : chaque lecture et chaque écriture passent par la mémoire commune.
    static volatile boolean ouverte = true;

    // volatile ici aussi, et pourtant ++ va perdre des ventes.
    static volatile int compteurVolatile = 0;

    static int compteurProtege = 0;
    static final Object verrou = new Object();

    static final AtomicInteger compteurAtomique = new AtomicInteger(0);

    static final int TOURS = 200_000;

    public static void main(String[] args) throws InterruptedException {
        visibilite();
        atomiciteVolatile();
        atomiciteSynchronized();
        atomiciteAtomicInteger();
    }

    /**
     * Problème de visibilité, réglé par volatile.
     * Le vendeur relit "ouverte" à chaque tour.
     * Quand main écrit false, le vendeur le voit et sort de la boucle.
     */
    static void visibilite() throws InterruptedException {
        Thread vendeur = new Thread(() -> {
            while (ouverte) {
                // une seule action : lire "ouverte"
            }
            System.out.println("visibilite     -> le vendeur a vu ouverte = false");
        });

        vendeur.start();
        Thread.sleep(50);
        ouverte = false; // une seule action : écrire false
        vendeur.join();
    }

    /**
     * ++ sur un volatile. Chaque lecture voit la dernière valeur,
     * mais ++ fait trois actions, et les deux threads se mélangent.
     */
    static void atomiciteVolatile() throws InterruptedException {
        Thread a = new Thread(() -> {
            for (int i = 0; i < TOURS; i++) {
                compteurVolatile++;
            }
        });
        Thread b = new Thread(() -> {
            for (int i = 0; i < TOURS; i++) {
                compteurVolatile++;
            }
        });

        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("volatile ++    -> " + compteurVolatile
                + "   (attendu " + (TOURS * 2) + ")");
    }

    /**
     * Le bloc synchronized englobe les trois actions de ++.
     * Un seul thread entre à la fois, et sa nouvelle valeur est visible
     * pour le thread qui entre ensuite.
     */
    static void atomiciteSynchronized() throws InterruptedException {
        Thread a = new Thread(() -> {
            for (int i = 0; i < TOURS; i++) {
                synchronized (verrou) {
                    compteurProtege++;
                }
            }
        });
        Thread b = new Thread(() -> {
            for (int i = 0; i < TOURS; i++) {
                synchronized (verrou) {
                    compteurProtege++;
                }
            }
        });

        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("synchronized   -> " + compteurProtege
                + "   (attendu " + (TOURS * 2) + ")");
    }

    /**
     * incrementAndGet() fait lire + ajouter + écrire en une seule opération.
     */
    static void atomiciteAtomicInteger() throws InterruptedException {
        Thread a = new Thread(() -> {
            for (int i = 0; i < TOURS; i++) {
                compteurAtomique.incrementAndGet();
            }
        });
        Thread b = new Thread(() -> {
            for (int i = 0; i < TOURS; i++) {
                compteurAtomique.incrementAndGet();
            }
        });

        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("AtomicInteger  -> " + compteurAtomique.get()
                + "   (attendu " + (TOURS * 2) + ")");
    }
}
