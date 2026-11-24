import styles from "./HomePage.module.scss";
import { Link } from "react-router-dom";

const HomePage = () => {
  return (
    <section className={styles.home}>
      <span className={styles.badge}>Budżet osobisty i rozliczenia grupowe</span>
      <h1>Kontroluj finanse i rozliczaj wspólne wydatki</h1>
      <p>
        Zapisuj przychody i wydatki, śledź bieżący bilans oraz dziel rachunki w
        grupach ze znajomymi z natychmiastowymi powiadomieniami.
      </p>
      <div className={styles.buttons}>
        <Link to="/add-transaction" className={styles.btn}>
          Dodaj Transakcję
        </Link>
        <Link to="/transactions" className={styles.btnSecondary}>
          Zobacz Listę
        </Link>
        <Link to="/groups" className={styles.btnSecondary}>
          Twoje Grupy
        </Link>
      </div>

      <div className={styles.features}>
        <div className={styles.featureCard}>
          <h3>Bieżący bilans</h3>
          <p>
            Filtruj przychody i wydatki według wybranego przedziału czasu w
            pasku podsumowania.
          </p>
        </div>
        <div className={styles.featureCard}>
          <h3>Podział w grupach</h3>
          <p>
            Twórz grupy, zapraszaj członków i automatycznie rozdzielaj wspólne
            koszty.
          </p>
        </div>
        <div className={styles.featureCard}>
          <h3>Potwierdzanie spłat</h3>
          <p>
            Oznaczaj spłacone długi i potwierdzaj rozliczenia między
            uczestnikami.
          </p>
        </div>
      </div>
    </section>
  );
};

export default HomePage;
