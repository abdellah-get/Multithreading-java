# Multithreading-java

Moteur concurrent de réservation de billets (Java 21).

## Prérequis

- JDK 21+
- Maven 3.9+

## Lancer les tests

```bash
mvn test
```

Preuve de la course (test volontairement rouge) :

```bash
mvn test -Dgroups=course -DexcludedGroups=
```

Pipeline + arrêt propre :

```bash
mvn "-Dtest=PipelineBookingTest,ShutdownTest" test
```

## Mesures et démos

```bash
mvn -q -DskipTests compile
java -cp target/classes booking.Phase3Mesure
java -cp target/classes booking.PinningDemo
java -cp target/classes booking.DeadlockDemo
java -cp target/classes booking.OrderedSwap
```
