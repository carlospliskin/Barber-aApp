import 'package:flutter/material.dart';

/// Sistema de espaciado — base 4px
abstract class AppSpacing {
  static const double xs = 4;
  static const double sm = 8;
  static const double md = 12;
  static const double base = 16;
  static const double lg = 20;
  static const double xl = 24;
  static const double xxl = 32;
  static const double xxxl = 48;
  static const double huge = 64;

  /// Padding horizontal de pantalla
  static const double screenH = 16;

  /// Padding vertical de pantalla
  static const double screenV = 20;

  /// Touch target mínimo (accesibilidad)
  static const double touchTarget = 44;

  static const EdgeInsets screen = EdgeInsets.symmetric(
    horizontal: screenH,
    vertical: screenV,
  );

  static const EdgeInsets screenHorizontal = EdgeInsets.symmetric(
    horizontal: screenH,
  );

  static const EdgeInsets cardPadding = EdgeInsets.all(md);
  static const EdgeInsets cardPaddingLg = EdgeInsets.all(base);
}

/// Sistema de bordes redondeados
abstract class AppRadius {
  static const double xs = 6;
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 20;
  static const double xxl = 28;
  static const double pill = 999;

  // Radius para componentes específicos
  static const double button = sm;
  static const double input = sm;
  static const double card = md;
  static const double cardLg = lg;
  static const double modal = xl;
  static const double bottomSheet = xxl;
  static const double badge = pill;
  static const double chip = pill;
  static const double avatar = pill;

  // BorderRadius helpers
  static BorderRadius get rXs => BorderRadius.circular(xs);
  static BorderRadius get rSm => BorderRadius.circular(sm);
  static BorderRadius get rMd => BorderRadius.circular(md);
  static BorderRadius get rLg => BorderRadius.circular(lg);
  static BorderRadius get rXl => BorderRadius.circular(xl);
  static BorderRadius get rXxl => BorderRadius.circular(xxl);
  static BorderRadius get rPill => BorderRadius.circular(pill);

  static BorderRadius get rButton => BorderRadius.circular(button);
  static BorderRadius get rInput => BorderRadius.circular(input);
  static BorderRadius get rCard => BorderRadius.circular(card);
  static BorderRadius get rCardLg => BorderRadius.circular(cardLg);
  static BorderRadius get rModal => BorderRadius.circular(modal);
  static BorderRadius get rBottomSheet => BorderRadius.only(
    topLeft: Radius.circular(bottomSheet),
    topRight: Radius.circular(bottomSheet),
  );
}
