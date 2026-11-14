import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_typography.dart';
import '../../core/theme/app_spacing.dart';

class AppTextField extends StatefulWidget {
  final String label;
  final String? hint;
  final TextEditingController? controller;
  final bool obscureText;
  final TextInputType keyboardType;
  final TextInputAction textInputAction;
  final String? errorText;
  final Widget? suffixIcon;
  final Widget? prefixIcon;
  final void Function(String)? onChanged;
  final void Function(String)? onSubmitted;
  final List<TextInputFormatter>? inputFormatters;
  final bool autofocus;
  final bool enabled;
  final int? maxLines;
  final FocusNode? focusNode;

  const AppTextField({
    super.key,
    required this.label,
    this.hint,
    this.controller,
    this.obscureText = false,
    this.keyboardType = TextInputType.text,
    this.textInputAction = TextInputAction.next,
    this.errorText,
    this.suffixIcon,
    this.prefixIcon,
    this.onChanged,
    this.onSubmitted,
    this.inputFormatters,
    this.autofocus = false,
    this.enabled = true,
    this.maxLines = 1,
    this.focusNode,
  });

  @override
  State<AppTextField> createState() => _AppTextFieldState();
}

class _AppTextFieldState extends State<AppTextField>
    with SingleTickerProviderStateMixin {
  late FocusNode _focusNode;
  late AnimationController _animController;
  late Animation<Color?> _borderColor;
  late Animation<Color?> _bgColor;
  bool _isFocused = false;

  @override
  void initState() {
    super.initState();
    _focusNode = widget.focusNode ?? FocusNode();
    _animController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 180),
    );
    _borderColor = ColorTween(
      begin: AppColors.smoke,
      end: AppColors.gold,
    ).animate(CurvedAnimation(parent: _animController, curve: Curves.easeOut));
    _bgColor = ColorTween(
      begin: AppColors.graphite,
      end: const Color(0xFF1C1A12), // graphite con tono cálido gold
    ).animate(CurvedAnimation(parent: _animController, curve: Curves.easeOut));

    _focusNode.addListener(() {
      setState(() => _isFocused = _focusNode.hasFocus);
      if (_focusNode.hasFocus) {
        _animController.forward();
      } else {
        _animController.reverse();
      }
    });
  }

  @override
  void dispose() {
    if (widget.focusNode == null) _focusNode.dispose();
    _animController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _animController,
      builder: (context, child) {
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              decoration: BoxDecoration(
                color: widget.errorText != null
                    ? AppColors.errorSubtle
                    : _bgColor.value,
                borderRadius: AppRadius.rInput,
                border: Border.all(
                  color: widget.errorText != null
                      ? AppColors.error
                      : (_borderColor.value ?? AppColors.smoke),
                  width: _isFocused ? 1.5 : 1.0,
                ),
              ),
              child: TextFormField(
                controller: widget.controller,
                focusNode: _focusNode,
                obscureText: widget.obscureText,
                keyboardType: widget.keyboardType,
                textInputAction: widget.textInputAction,
                onChanged: widget.onChanged,
                onFieldSubmitted: widget.onSubmitted,
                inputFormatters: widget.inputFormatters,
                autofocus: widget.autofocus,
                enabled: widget.enabled,
                maxLines: widget.obscureText ? 1 : widget.maxLines,
                style: AppTypography.bodyMd.copyWith(color: AppColors.white),
                cursorColor: AppColors.gold,
                decoration: InputDecoration(
                  labelText: widget.label,
                  hintText: widget.hint,
                  prefixIcon: widget.prefixIcon,
                  suffixIcon: widget.suffixIcon,
                  border: InputBorder.none,
                  enabledBorder: InputBorder.none,
                  focusedBorder: InputBorder.none,
                  errorBorder: InputBorder.none,
                  filled: false,
                  contentPadding: EdgeInsets.symmetric(
                    horizontal: AppSpacing.base,
                    vertical: widget.maxLines != null && widget.maxLines! > 1
                        ? AppSpacing.md
                        : AppSpacing.md,
                  ),
                  labelStyle: AppTypography.labelMd.copyWith(
                    color: _isFocused ? AppColors.gold : AppColors.ash,
                  ),
                  floatingLabelStyle: AppTypography.labelSm.copyWith(
                    color: AppColors.gold,
                    letterSpacing: 0.2,
                  ),
                  hintStyle: AppTypography.bodyMd.copyWith(
                    color: AppColors.ash,
                  ),
                ),
              ),
            ),
            if (widget.errorText != null) ...[
              const SizedBox(height: AppSpacing.xs),
              Row(
                children: [
                  const Icon(
                    Icons.error_outline_rounded,
                    size: 12,
                    color: AppColors.error,
                  ),
                  const SizedBox(width: 4),
                  Text(
                    widget.errorText!,
                    style: AppTypography.labelSm.copyWith(
                      color: AppColors.error,
                    ),
                  ),
                ],
              ),
            ],
          ],
        );
      },
    );
  }
}
