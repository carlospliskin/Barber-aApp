import 'package:flutter/material.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_typography.dart';
import '../../core/theme/app_spacing.dart';

// ─── AppCard ──────────────────────────────────────────────────────────────────

class AppCard extends StatelessWidget {
  final Widget child;
  final EdgeInsets? padding;
  final Color? color;
  final bool goldBorder;
  final VoidCallback? onTap;
  final double? borderRadius;

  const AppCard({
    super.key,
    required this.child,
    this.padding,
    this.color,
    this.goldBorder = false,
    this.onTap,
    this.borderRadius,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: padding ?? AppSpacing.cardPadding,
        decoration: BoxDecoration(
          color: color ?? AppColors.graphite,
          borderRadius: BorderRadius.circular(
            borderRadius ?? AppRadius.card,
          ),
          border: Border.all(
            color: goldBorder ? AppColors.gold.withOpacity(0.4) : AppColors.smoke,
          ),
          boxShadow: goldBorder ? AppColors.shadowGold : null,
        ),
        child: child,
      ),
    );
  }
}

// ─── AppBadge ─────────────────────────────────────────────────────────────────

enum AppBadgeVariant { confirmed, pending, cancelled, noShow, neutral }

class AppBadge extends StatelessWidget {
  final String label;
  final AppBadgeVariant variant;

  const AppBadge({
    super.key,
    required this.label,
    this.variant = AppBadgeVariant.neutral,
  });

  Color get _bg {
    switch (variant) {
      case AppBadgeVariant.confirmed: return AppColors.confirmedSubtle;
      case AppBadgeVariant.pending: return AppColors.pendingSubtle;
      case AppBadgeVariant.cancelled: return AppColors.errorSubtle;
      case AppBadgeVariant.noShow: return AppColors.noShowSubtle;
      case AppBadgeVariant.neutral: return AppColors.slate;
    }
  }

  Color get _text {
    switch (variant) {
      case AppBadgeVariant.confirmed: return AppColors.confirmed;
      case AppBadgeVariant.pending: return AppColors.gold;
      case AppBadgeVariant.cancelled: return AppColors.error;
      case AppBadgeVariant.noShow: return AppColors.noShow;
      case AppBadgeVariant.neutral: return AppColors.silver;
    }
  }

  Color get _border {
    switch (variant) {
      case AppBadgeVariant.confirmed: return AppColors.confirmed.withOpacity(0.3);
      case AppBadgeVariant.pending: return AppColors.gold.withOpacity(0.3);
      case AppBadgeVariant.cancelled: return AppColors.error.withOpacity(0.3);
      case AppBadgeVariant.noShow: return AppColors.noShow.withOpacity(0.3);
      case AppBadgeVariant.neutral: return AppColors.smoke;
    }
  }

  static AppBadgeVariant fromStatus(String status) {
    switch (status.toUpperCase()) {
      case 'CONFIRMED': return AppBadgeVariant.confirmed;
      case 'PENDING_CONFIRMATION': return AppBadgeVariant.pending;
      case 'CANCELLED': return AppBadgeVariant.cancelled;
      case 'NO_SHOW': return AppBadgeVariant.noShow;
      default: return AppBadgeVariant.neutral;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.sm + 2,
        vertical: 3,
      ),
      decoration: BoxDecoration(
        color: _bg,
        borderRadius: AppRadius.rPill,
        border: Border.all(color: _border),
      ),
      child: Text(
        label.toUpperCase(),
        style: AppTypography.labelSm.copyWith(
          color: _text,
          fontWeight: FontWeight.w700,
          letterSpacing: 0.3,
        ),
      ),
    );
  }
}

// ─── AppDivider ───────────────────────────────────────────────────────────────

class AppDivider extends StatelessWidget {
  final String? label;
  final EdgeInsets? margin;

  const AppDivider({super.key, this.label, this.margin});

  @override
  Widget build(BuildContext context) {
    if (label == null) {
      return Container(
        margin: margin ?? const EdgeInsets.symmetric(vertical: AppSpacing.md),
        height: 1,
        color: AppColors.smoke,
      );
    }
    return Container(
      margin: margin ?? const EdgeInsets.symmetric(vertical: AppSpacing.md),
      child: Row(
        children: [
          Expanded(child: Container(height: 1, color: AppColors.smoke)),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md),
            child: Text(
              label!,
              style: AppTypography.labelSm.copyWith(color: AppColors.ash),
            ),
          ),
          Expanded(child: Container(height: 1, color: AppColors.smoke)),
        ],
      ),
    );
  }
}

// ─── AppLogoMark ──────────────────────────────────────────────────────────────

class AppLogoMark extends StatelessWidget {
  final double size;

  const AppLogoMark({super.key, this.size = 64});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        border: Border.all(color: AppColors.gold, width: 1.5),
        borderRadius: BorderRadius.circular(size * 0.22),
        boxShadow: AppColors.shadowGold,
      ),
      child: Stack(
        alignment: Alignment.center,
        children: [
          // Inner border decorativo
          Positioned.fill(
            child: Padding(
              padding: EdgeInsets.all(size * 0.08),
              child: Container(
                decoration: BoxDecoration(
                  border: Border.all(
                    color: AppColors.gold.withOpacity(0.25),
                    width: 0.5,
                  ),
                  borderRadius: BorderRadius.circular(size * 0.14),
                ),
              ),
            ),
          ),
          Text(
            'B',
            style: TextStyle(
              fontFamily: 'PlayfairDisplay',
              fontSize: size * 0.45,
              fontWeight: FontWeight.w900,
              color: AppColors.gold,
              height: 1,
            ),
          ),
        ],
      ),
    );
  }
}

// ─── GoldGlowContainer ────────────────────────────────────────────────────────

class GoldGlowContainer extends StatelessWidget {
  final Widget child;

  const GoldGlowContainer({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: AppSpacing.cardPaddingLg,
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            AppColors.gold.withOpacity(0.12),
            AppColors.gold.withOpacity(0.04),
          ],
        ),
        borderRadius: AppRadius.rCard,
        border: Border.all(color: AppColors.gold.withOpacity(0.25)),
      ),
      child: child,
    );
  }
}
