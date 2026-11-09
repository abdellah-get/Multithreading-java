# Multithreading-java

Moteur concurrent de réservation de billets de concert, en **Java 21**.

Un concert a **200 places**. Des milliers de clients réservent en même temps. Le moteur doit :

- ne jamais vendre la même place deux fois ;
- rester réactif pendant un paiement lent ;
- s’arrêter proprement à la fin de la vente.

Le projet suit le sujet *Java 21 Multithreading* (quiz + mini-projet) : course critique, verrous, file de paiement, virtual threads, shutdown et deadlock.

## Prérequis

- JDK 21 ou plus récent
- Maven 3.9+

Vérifier :

```bash
java -version
mvn -version
```

## Structure du projet

```text
src/main/java/booking/
  Seat, SeatState, BookingRequest, BookingResult, BookingEngine
  NaiveBookingEngine              # sans protection (montre le bug)
  SynchronizedBookingEngine       # un synchronized global
  LockPerSeatBookingEngine        # un ReentrantLock par place
  CasBookingEngine                # compareAndSet
  PaymentGateway                  # paiement lent (50–300 ms, ~10 % d’échecs)
  PipelineBookingEngine           # FREE → HELD → SOLD + file + expiration
  Phase3Mesure                    # 50 threads plateforme vs virtual threads
  PinningDemo                     # synchronized vs ReentrantLock
  DeadlockDemo                    # deadlock volontaire
  OrderedSwap                     # correction par ordre des verrous
  Vitesse                         # mesure des 3 corrections phase 1

src/test/java/booking/
  CourseNaiveTest                 # preuve de la course (volontairement rouge)
  ToutesLesVersionsTest           # naïf + 3 corrections
  SynchronizedBookingTest
  PipelineBookingTest
  ShutdownTest
  OrderedSwapTest
```

## Les trois phases

### Phase 1 — La course

1. `NaiveBookingEngine` lit `FREE` puis écrit `SOLD` sans protection.
2. Un test de charge (1 000 clients, 200 places) montre des doubles ventes.
3. Trois corrections, toutes correctes :
   - `synchronized` sur tout le moteur ;
   - un `ReentrantLock` par place ;
   - `AtomicReferenceArray` + `compareAndSet`.

### Phase 2 — Retenue et paiement

```text
FREE → HELD → SOLD
         ↓
        FREE   (paiement échoué ou retenue > 2 s)
```

- retenue atomique `FREE` → `HELD` ;
- file bornée de paiements (`offer` 150 ms, sinon `Rejected("busy")`) ;
- 8 workers appellent `PaymentGateway.charge` ;
- expiration à 2 secondes → `Expired` ;
- compteurs + affichage une fois par seconde.

Checkpoint : `SOLD == Confirmed` et `Confirmed + Rejected + Expired == nombre de demandes`.

### Phase 3 — Arrêt, virtual threads, pinning

- `shutdown()` refuse les nouvelles demandes, vide la file, arrête workers / scheduler / rapport, puis `awaitTermination` ;
- mesure : 50 threads plateforme vs 10 000 virtual threads ;
- pinning : ne pas appeler `charge()` dans un `synchronized` ;
- deadlock d’échange de places, puis correction (ordre croissant des IDs).

## Lancer les tests

Tous les tests verts (sauf le test de course, exclu par défaut) :

```bash
mvn test
```

Preuve de la course (ce test **doit échouer**) :

```bash
mvn test -Dgroups=course -DexcludedGroups=
```

Pipeline + arrêt propre :

```bash
mvn "-Dtest=PipelineBookingTest,ShutdownTest" test
```

Échange de places sans deadlock :

```bash
mvn "-Dtest=OrderedSwapTest" test
```

## Mesures et démos

Compiler sans lancer les tests :

```bash
mvn -q -DskipTests compile
```

| Commande | Rôle |
|---|---|
| `java -cp target/classes booking.Vitesse` | temps des 3 corrections phase 1 |
| `java -cp target/classes booking.Phase3Mesure` | 50 platform threads vs virtual threads |
| `java -cp target/classes booking.PinningDemo` | pinning `synchronized` vs `ReentrantLock` |
| `java -cp target/classes booking.DeadlockDemo` | deadlock volontaire (+ détection intégrée) |
| `java -cp target/classes booking.OrderedSwap` | même échange, sans deadlock |

`DeadlockDemo` reste bloqué après l’affichage du cycle. Quitter avec `Ctrl+C`.

Option dump externe (si l’attache JVM fonctionne) :

```bash
jcmd <pid> Thread.print
```

## Résultats attendus (exemples)

**Phase 1 — sans correction** (extrait) :

```text
essai : 226 confirmations, place 199 vendue 5 fois
```

**Phase 1 — avec correction** (20 fois) :

```text
essai : 200 confirmations, place 0 vendue 1 fois
```

**Phase 2** :

```text
resultats confirmed=178 rejected=72 expired=0 sold=178
```

(`sold` = `confirmed`, et la somme des résultats = nombre de demandes)

**Phase 3 — mesure** (exemple sur une machine) :

```text
plateforme : ~413 ms, picThreads≈68
virtual    : ~323 ms, picThreads≈28
```

Les virtual threads aident parce que `charge()` **dort**. Si `charge()` était CPU-bound, le gain disparaîtrait.

## Concepts couverts

| Concept | Où le voir |
|---|---|
| Processus / thread, course, check-then-act | `NaiveBookingEngine`, `CourseNaiveTest` |
| `synchronized` | `SynchronizedBookingEngine` |
| `ReentrantLock` | `LockPerSeatBookingEngine`, `OrderedSwap` |
| `compareAndSet` | `CasBookingEngine` |
| `BlockingQueue`, workers | `PipelineBookingEngine` |
| Expiration / `ScheduledExecutorService` | `PipelineBookingEngine` |
| Shutdown + interruption | `PipelineBookingEngine.shutdown`, `ShutdownTest` |
| Virtual threads | `Phase3Mesure` |
| Pinning | `PinningDemo` |
| Deadlock | `DeadlockDemo`, `OrderedSwap` |

## Auteurs

Projet de groupe — Java 21 Multithreading.
