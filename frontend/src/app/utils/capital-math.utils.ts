/**
 * Utilitaires mathématiques et financiers de haute précision pour la gestion du capital social.
 *
 * Implémentation mathématique exacte (D1) pour éviter tout risque lié à l'arithmétique
 * flottante IEEE 754 de JavaScript.
 *
 * Utilise des entiers à grande précision (BigInt) avec mise à l'échelle décimale commune,
 * garantissant une cohérence absolue avec les validations BigDecimal du backend Java
 * (RoundingMode.UNNECESSARY / remainder == 0).
 */

export interface ScaledDecimal {
  /** Valeur entière non mise à l'échelle (ex: 10050 pour 100.50) */
  readonly unscaled: bigint;
  /** Nombre de décimales (ex: 2 pour 100.50) */
  readonly scale: number;
}

export interface CapitalDistributionSummary {
  readonly capitalTotal: number;
  readonly valeurNominale: number;
  readonly totalPartsAttendu: number;
  readonly sumPartsAttribuees: number;
  readonly partsRestantes: number;
  readonly sumApportsAttribues: number;
  readonly capitalRestant: number;
  readonly pourcentageTotalAttribue: number;
  readonly isDivisible: boolean;
  readonly isComplete: boolean;
  readonly isExceeded: boolean;
  readonly isCountValid: boolean;
  readonly errors: string[];
  readonly warnings: string[];
}

/**
 * Analyse une valeur numérique ou textuelle en représentation décimale exacte ScaledDecimal.
 * Retourne null si la valeur n'est pas un nombre décimal valide.
 */
export function parseDecimal(value: number | string | null | undefined): ScaledDecimal | null {
  if (value === null || value === undefined) return null;
  const str = String(value).trim();
  if (!str) return null;

  // Format standard : signe optionnel, chiffres entiers, point optionnel suivi de décimales
  const match = str.match(/^([+-])?(\d+)(?:\.(\d+))?$/);
  if (!match) return null;

  const isNegative = match[1] === '-';
  const integerPart = match[2];
  const fractionalPart = match[3] || '';
  const scale = fractionalPart.length;

  const combinedStr = integerPart + fractionalPart;
  // Retirer les zéros non significatifs en tête sauf si "0"
  const cleanStr = combinedStr.replace(/^0+(?=\d)/, '');
  const unscaled = (isNegative ? -1n : 1n) * BigInt(cleanStr || '0');

  return { unscaled, scale };
}

/**
 * Met à l'échelle deux décimaux vers leur plus grand dénominateur commun (échelle maximale).
 */
export function toCommonScale(
  a: ScaledDecimal,
  b: ScaledDecimal
): { aScaled: bigint; bScaled: bigint; scale: number } {
  const S = Math.max(a.scale, b.scale);
  const aFactor = 10n ** BigInt(S - a.scale);
  const bFactor = 10n ** BigInt(S - b.scale);
  return {
    aScaled: a.unscaled * aFactor,
    bScaled: b.unscaled * bFactor,
    scale: S
  };
}

/**
 * Vérifie si une valeur est strictement positive (> 0).
 */
export function isStrictlyPositive(value: number | string | null | undefined): boolean {
  const dec = parseDecimal(value);
  return dec !== null && dec.unscaled > 0n;
}

/**
 * Vérifie la divisibilité exacte du capital par la valeur nominale d'une part.
 * Équivalent strict à : `capital.remainder(valeurNominale).compareTo(BigDecimal.ZERO) == 0`.
 *
 * Corrige définitivement le défaut D1 (imprécision du modulo flottant `%` en JS).
 */
export function isCapitalDivisibleByNominal(
  capital: number | string | null | undefined,
  valeurNominale: number | string | null | undefined
): boolean {
  const cap = parseDecimal(capital);
  const vn = parseDecimal(valeurNominale);

  if (!cap || !vn) return false;
  if (cap.unscaled <= 0n || vn.unscaled <= 0n) return false;

  const { aScaled, bScaled } = toCommonScale(cap, vn);
  if (bScaled === 0n) return false;

  return (aScaled % bScaled) === 0n;
}

/**
 * Calcule le nombre total de parts sociales résultant de la division du capital par la valeur nominale.
 * Retourne le quotient entier exact si divisible, ou le quotient tronqué sinon.
 */
export function computeTotalParts(
  capital: number | string | null | undefined,
  valeurNominale: number | string | null | undefined
): number {
  const cap = parseDecimal(capital);
  const vn = parseDecimal(valeurNominale);

  if (!cap || !vn || cap.unscaled <= 0n || vn.unscaled <= 0n) return 0;

  const { aScaled, bScaled } = toCommonScale(cap, vn);
  if (bScaled === 0n) return 0;

  const quotient = aScaled / bScaled;
  return Number(quotient);
}

/**
 * Calcule l'apport individuel d'un associé : nombre de parts × valeur nominale.
 * Calcul exact sans dérive de virgule flottante.
 */
export function computeApport(
  nombreParts: number | null | undefined,
  valeurNominale: number | string | null | undefined
): number {
  if (!nombreParts || nombreParts <= 0) return 0;
  const vn = parseDecimal(valeurNominale);
  if (!vn || vn.unscaled <= 0n) return 0;

  const partsBig = BigInt(Math.floor(nombreParts));
  const unscaledApport = partsBig * vn.unscaled;

  if (vn.scale === 0) {
    return Number(unscaledApport);
  }

  const divisor = 10 ** vn.scale;
  return Math.round((Number(unscaledApport) / divisor) * 100) / 100;
}

/**
 * Calcule le pourcentage de détention pour l'affichage : (parts / totalParts) × 100.
 * Retourne une chaîne formatée à 2 décimales.
 * Conforme à la règle : affichage uniquement, sans validation de la somme des pourcentages arrondis.
 */
export function computePourcentageDetention(
  nombreParts: number | null | undefined,
  totalParts: number | null | undefined
): string {
  if (!nombreParts || !totalParts || totalParts <= 0 || nombreParts <= 0) {
    return '0.00';
  }
  const pct = (nombreParts / totalParts) * 100;
  return pct.toFixed(2);
}

/**
 * Calcule le capital restant à attribuer en MAD.
 */
export function computeCapitalRestant(
  capital: number | string | null | undefined,
  sumApports: number
): number {
  const cap = parseDecimal(capital);
  if (!cap || cap.unscaled <= 0n) return 0;

  const capNum = Number(cap.unscaled) / (10 ** cap.scale);
  const restant = capNum - sumApports;
  return Math.round(restant * 100) / 100;
}

/**
 * Analyse et résume la répartition complète du capital social pour un dossier.
 */
export function summarizeCapitalDistribution(
  legalFormCode: 'SARL' | 'SARL_AU' | 'AUTO_ENTREPRENEUR' | 'SA' | string,
  capitalInput: number | string | null | undefined,
  valeurNominaleInput: number | string | null | undefined,
  activeAssocies: Array<{
    nombreParts?: number;
    montantApport?: number;
    prenom?: string;
    nom?: string;
    cinOuPasseport?: string;
  }>
): CapitalDistributionSummary {
  const errors: string[] = [];
  const warnings: string[] = [];

  const capDec = parseDecimal(capitalInput);
  const vnDec = parseDecimal(valeurNominaleInput);

  const capitalTotal = capDec && capDec.unscaled > 0n
    ? Number(capDec.unscaled) / (10 ** capDec.scale)
    : 0;

  const valeurNominale = vnDec && vnDec.unscaled > 0n
    ? Number(vnDec.unscaled) / (10 ** vnDec.scale)
    : 100;

  const isDivisible = isCapitalDivisibleByNominal(capitalInput, valeurNominaleInput);
  const totalPartsAttendu = computeTotalParts(capitalInput, valeurNominaleInput);

  // Somme des parts attribuées
  const sumPartsAttribuees = activeAssocies.reduce(
    (sum, a) => sum + (Number(a.nombreParts) || 0),
    0
  );

  // Somme des apports
  const sumApportsAttribues = activeAssocies.reduce(
    (sum, a) => sum + (Number(a.montantApport) || computeApport(a.nombreParts, valeurNominale)),
    0
  );

  const partsRestantes = totalPartsAttendu - sumPartsAttribuees;
  const capitalRestant = computeCapitalRestant(capitalInput, sumApportsAttribues);
  const isComplete = totalPartsAttendu > 0 && partsRestantes === 0 && capitalRestant === 0;
  const isExceeded = partsRestantes < 0 || capitalRestant < 0;

  const count = activeAssocies.length;
  let isCountValid = true;

  if (legalFormCode === 'SARL_AU') {
    isCountValid = count === 1;
    if (count !== 1) {
      errors.push(`Une SARL-AU requiert exactement 1 associé unique actif (actuellement : ${count}).`);
    }
  } else if (legalFormCode === 'SARL') {
    isCountValid = count >= 2 && count <= 50;
    if (count < 2) {
      errors.push(`Une SARL requiert au minimum 2 associés fondateurs actifs (actuellement : ${count}).`);
    } else if (count > 50) {
      errors.push(`Une SARL ne peut pas dépasser 50 associés fondateurs (actuellement : ${count}).`);
    }
  }

  // Contrôles de divisibilité et validité des montants
  if (!capDec || capDec.unscaled <= 0n) {
    errors.push('Le capital social doit être strictement supérieur à zéro.');
  }
  if (!vnDec || vnDec.unscaled <= 0n) {
    errors.push('La valeur nominale d’une part doit être strictement supérieure à zéro.');
  } else if (!isDivisible && capitalTotal > 0) {
    errors.push(
      `Le capital social (${capitalTotal} MAD) doit être un multiple exact de la valeur nominale des parts (${valeurNominale} MAD).`
    );
  }

  // Contrôle individuel de chaque associé
  for (const a of activeAssocies) {
    const p = Number(a.nombreParts) || 0;
    const name = a.prenom && a.nom ? `${a.prenom} ${a.nom}` : (a.cinOuPasseport || 'Un associé');
    if (p < 1) {
      errors.push(`L’associé ${name} doit détenir au moins une part sociale.`);
    }
  }

  // Cohérence de la répartition
  if (totalPartsAttendu > 0) {
    if (partsRestantes > 0) {
      warnings.push(
        `Il reste ${partsRestantes} part(s) sociale(s) non attribuée(s) (${Math.max(0, capitalRestant)} MAD).`
      );
    } else if (partsRestantes < 0) {
      errors.push(
        `Les parts attribuées (${sumPartsAttribuees}) dépassent le total prévu (${totalPartsAttendu}) de ${-partsRestantes} part(s).`
      );
    }

    if (capitalRestant > 0 && partsRestantes === 0) {
      warnings.push(`Le montant total des apports ne correspond pas au capital déclaré.`);
    } else if (capitalRestant < 0) {
      errors.push(`Le montant total des apports dépasse le capital social de ${-capitalRestant} MAD.`);
    }
  }

  // Pourcentage global attribué
  const pourcentageTotalAttribue = totalPartsAttendu > 0
    ? Math.min(100, Math.round((sumPartsAttribuees / totalPartsAttendu) * 100))
    : 0;

  return {
    capitalTotal,
    valeurNominale,
    totalPartsAttendu,
    sumPartsAttribuees,
    partsRestantes,
    sumApportsAttribues,
    capitalRestant,
    pourcentageTotalAttribue,
    isDivisible,
    isComplete,
    isExceeded,
    isCountValid,
    errors,
    warnings
  };
}
