import 'package:flutter/material.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_typography.dart';
import '../../core/theme/app_spacing.dart';

enum AppButtonVariant { primary, outline, ghost, danger }
enum AppButtonSize { sm, md, lg }

class AppButton extends StatefulWidget {
  final String label;
  final VoidCallback? onTap;
  final AppButtonVariant variant;
  final AppButtonSize size;
  final Widget? leading;
  final Widget? trailing;
  final bool loading;
  final bool fullWidth;
  final IconData? icon;

  const AppButton({
    super.key,
    required this.label,
    this.onTap,
    this.variant = AppButtonVariant.primary,
    this.size = AppButtonSize.md,
    this.leading,
    this.trailing,
    this.loading = false,
    this.fullWidth = true,
    this.icon,
  });

  @override
  State<AppButton> createState() => _AppButtonState();
}

class _AppButtonState extends State<AppButton>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _scale;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 100),
    );
    _scale = Tween<double>(begin: 1.0, end: 0.97).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  double get _height {
    switch (widget.size) {
      case AppButtonSize.sm: return 36;
      case AppButtonSize.md: return AppSpacing.touchTarget;
      case AppButtonSize.lg: return 52;
    }
  }

  EdgeInsets get _padding {
    switch (widget.size) {
      case AppButtonSize.sm: return const EdgeInsets.symmetric(horizontal: AppSpacing.md);
      case AppButtonSize.md: return const EdgeInsets.symmetric(horizontal: AppSpacing.xl);
      case AppButtonSize.lg: return const EdgeInsets.symmetric(horizontal: AppSpacing.xxl);
    }
  }

  TextStyle get _textStyle {
    switch (widget.size) {
      case AppButtonSize.sm: return AppTypography.labelMd.copyWith(fontWeight: FontWeight.w600);
      case AppButtonSize.md: return AppTypography.labelLg.copyWith(fontSize: 14, fontWeight: FontWeight.w700);
      case AppButtonSize.lg: return AppTypography.labelLg.copyWith(fontSize: 15, fontWeight: FontWeight.w700);
    }
  }

  Color get _bgColor {
    if (widget.onTap == null) return AppColors.smoke;
    switch (widget.variant) {
      case AppButtonVariant.primary: return AppColors.gold;
      case AppButtonVariant.outline: return Colors.transparent;
      case AppButtonVariant.ghost: return AppColors.graphite;
      case AppButtonVariant.danger: return AppColors.errorSubtle;
    }
  }

  Color get _textColor {
    if (widget.onTap == null) return AppColors.ash;
    switch (widget.variant) {
      case AppButtonVariant.primary: return AppColors.obsidian;
      case AppButtonVariant.outline: return AppColors.gold;
      case AppButtonVariant.ghost: return AppColors.pearl;
      case AppButtonVariant.danger: return AppColors.error;
    }
  }

  Border? get _border {
    switch (widget.variant) {
      case AppButtonVariant.outline:
        return Border.all(color: widget.onTap == null ? AppColors.smoke : AppColors.gold);
      case AppButtonVariant.ghost:
        return Border.all(color: AppColors.smoke);
      case AppButtonVariant.danger:
        return Border.all(color: AppColors.error.withOpacity(0.4));
      default:
        return null;
    }
  }

  List<BoxShadow>? get _shadows {
    if (widget.variant == AppButtonVariant.primary && widget.onTap != null) {
      return AppColors.shadowGoldBtn;
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: widget.onTap != null ? (_) => _controller.forward() : null,
      onTapUp: widget.onTap != null ? (_) => _controller.reverse() : null,
      onTapCancel: () => _controller.reverse(),
      onTap: widget.loading ? null : widget.onTap,
      child: ScaleTransition(
        scale: _scale,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          height: _height,
          width: widget.fullWidth ? double.infinity : null,
          padding: _padding,
          decoration: BoxDecoration(
            color: _bgColor,
            gradient: widget.variant == AppButtonVariant.primary && widget.onTap != null
                ? AppColors.goldGradient
                : null,
            borderRadius: AppRadius.rButton,
            border: _border,
            boxShadow: _shadows,
          ),
          child: widget.loading
              ? Center(
                  child: SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: _textColor,
                    ),
                  ),
                )
              : Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  mainAxisSize: widget.fullWidth ? MainAxisSize.max : MainAxisSize.min,
                  children: [
                    if (widget.leading != null) ...[
                      widget.leading!,
                      const SizedBox(width: AppSpacing.sm),
                    ],
                    if (widget.icon != null) ...[
                      Icon(widget.icon, size: 16, color: _textColor),
                      const SizedBox(width: AppSpacing.sm),
                    ],
                    Text(
                      widget.label,
                      style: _textStyle.copyWith(color: _textColor),
                    ),
                    if (widget.trailing != null) ...[
                      const SizedBox(width: AppSpacing.sm),
                      widget.trailing!,
                    ],
                  ],
                ),
        ),
      ),
    );
  }
}
