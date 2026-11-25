import {
  isCapitalDivisibleByNominal,
  computeTotalParts,
  computeApport,
  computePourcentageDetention,
  computeCapitalRestant,
  summarizeCapitalDistribution,
  parseDecimal,
  toCommonScale
} from './capital-math.utils';

describe('CapitalMathUtils', () => {
  describe('parseDecimal & toCommonScale', () => {
    it('should parse integers correctly', () => {
      const dec = parseDecimal(100000);
      expect(dec).not.toBeNull();
      expect(dec!.unscaled).toBe(100000n);
      expect(dec!.scale).toBe(0);
    });

    it('should parse string decimals with arbitrary scale', () => {
      const dec = parseDecimal('100.50');
      expect(dec).not.toBeNull();
      expect(dec!.unscaled).toBe(10050n);
      expect(dec!.scale).toBe(2);
    });

    it('should scale decimals to common scale without precision loss', () => {
      const a = parseDecimal('100.5')!;
      const b = parseDecimal('0.50')!;
      const { aScaled, bScaled, scale } = toCommonScale(a, b);
      expect(scale).toBe(2);
      expect(aScaled).toBe(10050n);
      expect(bScaled).toBe(50n);
      expect(aScaled % bScaled).toBe(0n);
    });

    it('should return null for invalid non-numeric inputs', () => {
      expect(parseDecimal('')).toBeNull();
      expect(parseDecimal('abc')).toBeNull();
      expect(parseDecimal(null)).toBeNull();
      expect(parseDecimal(undefined)).toBeNull();
    });
  });

  describe('isCapitalDivisibleByNominal (Correction D1)', () => {
    it('should validate exact integer divisibility', () => {
      expect(isCapitalDivisibleByNominal(100000, 100)).toBeTrue();
      expect(isCapitalDivisibleByNominal(300000, 100)).toBeTrue();
      expect(isCapitalDivisibleByNominal(50000, 50)).toBeTrue();
      expect(isCapitalDivisibleByNominal(1000, 10)).toBeTrue();
    });

    it('should reject integer non-divisible capital', () => {
      expect(isCapitalDivisibleByNominal(100000, 33)).toBeFalse();
      expect(isCapitalDivisibleByNominal(100000, 30)).toBeFalse();
      expect(isCapitalDivisibleByNominal(100, 3)).toBeFalse();
    });

    it('should validate exact decimal divisibility', () => {
      expect(isCapitalDivisibleByNominal('100000.50', '0.50')).toBeTrue();
      expect(isCapitalDivisibleByNominal(10000.25, 0.25)).toBeTrue();
      expect(isCapitalDivisibleByNominal('100.50', '0.50')).toBeTrue();
    });

    it('should resolve the IEEE 754 floating point trap (e.g. 100000.7 / 0.1)', () => {
      // In native JS: 100000.7 % 0.1 !== 0 due to float representation
      expect(isCapitalDivisibleByNominal('100000.7', '0.1')).toBeTrue();
      expect(isCapitalDivisibleByNominal(100000.7, 0.1)).toBeTrue();
    });

    it('should reject decimal non-divisible capital', () => {
      expect(isCapitalDivisibleByNominal('100000.50', 100)).toBeFalse();
      expect(isCapitalDivisibleByNominal('100.55', '0.50')).toBeFalse();
      expect(isCapitalDivisibleByNominal('100001.5', 100)).toBeFalse();
    });

    it('should reject zero or negative capital or nominal values', () => {
      expect(isCapitalDivisibleByNominal(0, 100)).toBeFalse();
      expect(isCapitalDivisibleByNominal(100000, 0)).toBeFalse();
      expect(isCapitalDivisibleByNominal(-100000, 100)).toBeFalse();
      expect(isCapitalDivisibleByNominal(100000, -100)).toBeFalse();
      expect(isCapitalDivisibleByNominal(null, 100)).toBeFalse();
      expect(isCapitalDivisibleByNominal(100000, null)).toBeFalse();
    });
  });

  describe('computeTotalParts', () => {
    it('should compute exact total parts for integer amounts', () => {
      expect(computeTotalParts(100000, 100)).toBe(1000);
      expect(computeTotalParts(300000, 100)).toBe(3000);
      expect(computeTotalParts(50000, 50)).toBe(1000);
    });

    it('should compute exact total parts for decimal amounts', () => {
      expect(computeTotalParts('100000.50', '0.50')).toBe(200001);
      expect(computeTotalParts('100.50', '0.50')).toBe(201);
    });

    it('should return 0 for invalid or non-positive inputs', () => {
      expect(computeTotalParts(0, 100)).toBe(0);
      expect(computeTotalParts(100000, 0)).toBe(0);
      expect(computeTotalParts(-100, 100)).toBe(0);
    });
  });

  describe('computeApport', () => {
    it('should compute exact contribution for integer parts and nominal', () => {
      expect(computeApport(500, 100)).toBe(50000);
      expect(computeApport(1, 100)).toBe(100);
      expect(computeApport(1000, 100)).toBe(100000);
    });

    it('should compute exact contribution for decimal nominal values', () => {
      expect(computeApport(200, 0.5)).toBe(100);
      expect(computeApport(1, 12.25)).toBe(12.25);
      expect(computeApport(4, '0.25')).toBe(1);
    });

    it('should return 0 for zero or negative parts', () => {
      expect(computeApport(0, 100)).toBe(0);
      expect(computeApport(-5, 100)).toBe(0);
    });
  });

  describe('computePourcentageDetention', () => {
    it('should compute percentage with 2 decimal places for display', () => {
      expect(computePourcentageDetention(500, 1000)).toBe('50.00');
      expect(computePourcentageDetention(1000, 1000)).toBe('100.00');
      expect(computePourcentageDetention(1, 3)).toBe('33.33');
      expect(computePourcentageDetention(2, 3)).toBe('66.67');
    });

    it('should return 0.00 for invalid values', () => {
      expect(computePourcentageDetention(0, 1000)).toBe('0.00');
      expect(computePourcentageDetention(500, 0)).toBe('0.00');
    });
  });

  describe('computeCapitalRestant', () => {
    it('should compute remaining capital accurately', () => {
      expect(computeCapitalRestant(100000, 50000)).toBe(50000);
      expect(computeCapitalRestant(100000, 100000)).toBe(0);
      expect(computeCapitalRestant(100000, 110000)).toBe(-10000);
      expect(computeCapitalRestant('100.50', 50.25)).toBe(50.25);
    });
  });

  describe('summarizeCapitalDistribution', () => {
    it('should validate a complete SARL with 2 partners', () => {
      const summary = summarizeCapitalDistribution(
        'SARL',
        100000,
        100,
        [
          { prenom: 'Ahmed', nom: 'Ben', nombreParts: 500, montantApport: 50000 },
          { prenom: 'Sara', nom: 'Idrissi', nombreParts: 500, montantApport: 50000 }
        ]
      );
      expect(summary.isComplete).toBeTrue();
      expect(summary.isDivisible).toBeTrue();
      expect(summary.isCountValid).toBeTrue();
      expect(summary.totalPartsAttendu).toBe(1000);
      expect(summary.sumPartsAttribuees).toBe(1000);
      expect(summary.partsRestantes).toBe(0);
      expect(summary.capitalRestant).toBe(0);
      expect(summary.errors.length).toBe(0);
      expect(summary.warnings.length).toBe(0);
    });

    it('should validate a complete SARL-AU with 1 partner holding 100%', () => {
      const summary = summarizeCapitalDistribution(
        'SARL_AU',
        100000,
        100,
        [
          { prenom: 'Karim', nom: 'Alaoui', nombreParts: 1000, montantApport: 100000 }
        ]
      );
      expect(summary.isComplete).toBeTrue();
      expect(summary.isCountValid).toBeTrue();
      expect(summary.totalPartsAttendu).toBe(1000);
      expect(summary.partsRestantes).toBe(0);
      expect(summary.errors.length).toBe(0);
    });

    it('should report warnings when shares are missing', () => {
      const summary = summarizeCapitalDistribution(
        'SARL',
        100000,
        100,
        [
          { prenom: 'Ahmed', nom: 'Ben', nombreParts: 400, montantApport: 40000 },
          { prenom: 'Sara', nom: 'Idrissi', nombreParts: 400, montantApport: 40000 }
        ]
      );
      expect(summary.isComplete).toBeFalse();
      expect(summary.partsRestantes).toBe(200);
      expect(summary.capitalRestant).toBe(20000);
      expect(summary.warnings.some(w => w.includes('200 part(s)'))).toBeTrue();
    });

    it('should report errors when shares exceed total capital', () => {
      const summary = summarizeCapitalDistribution(
        'SARL',
        100000,
        100,
        [
          { prenom: 'Ahmed', nom: 'Ben', nombreParts: 600, montantApport: 60000 },
          { prenom: 'Sara', nom: 'Idrissi', nombreParts: 500, montantApport: 50000 }
        ]
      );
      expect(summary.isExceeded).toBeTrue();
      expect(summary.partsRestantes).toBe(-100);
      expect(summary.errors.some(e => e.includes('dépassent'))).toBeTrue();
    });

    it('should report error when SARL has only 1 partner', () => {
      const summary = summarizeCapitalDistribution(
        'SARL',
        100000,
        100,
        [
          { prenom: 'Ahmed', nom: 'Ben', nombreParts: 1000, montantApport: 100000 }
        ]
      );
      expect(summary.isCountValid).toBeFalse();
      expect(summary.errors.some(e => e.includes('au minimum 2 associés'))).toBeTrue();
    });

    it('should report error when a partner has 0 parts', () => {
      const summary = summarizeCapitalDistribution(
        'SARL',
        100000,
        100,
        [
          { prenom: 'Ahmed', nom: 'Ben', nombreParts: 1000, montantApport: 100000 },
          { prenom: 'Sara', nom: 'Idrissi', nombreParts: 0, montantApport: 0 }
        ]
      );
      expect(summary.errors.some(e => e.includes('au moins une part'))).toBeTrue();
    });

    it('should report error when capital is not divisible by nominal value', () => {
      const summary = summarizeCapitalDistribution(
        'SARL',
        100000,
        33,
        [
          { prenom: 'Ahmed', nom: 'Ben', nombreParts: 1500, montantApport: 49500 },
          { prenom: 'Sara', nom: 'Idrissi', nombreParts: 1500, montantApport: 49500 }
        ]
      );
      expect(summary.isDivisible).toBeFalse();
      expect(summary.errors.some(e => e.includes('multiple exact'))).toBeTrue();
    });
  });
});
