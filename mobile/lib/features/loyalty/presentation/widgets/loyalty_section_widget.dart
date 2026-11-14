import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../shared/widgets/app_components.dart';
import '../../domain/entities/loyalty_entities.dart';

// ─── OpenStatusToggle ─────────────────────────────────────────────────────────
// Botón principal de abrir/cerrar la barbería

class OpenStatusToggle extends StatefulWidget {
  final TenantOpenStatus status;
  final Future<void> Function(bool isOpen, String? reason) onToggle;

  const OpenStatusToggle({
    super.key,
    required this.status,
    required this.onToggle,
  });

  @override
  State<OpenStatusToggle> createState() => _OpenStatusToggleState();
}

class _OpenStatusToggleState extends State<OpenStatusToggle>
    with SingleTickerProviderStateMixin {
  late AnimationController _pulse;
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _pulse = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    );
    if (widget.status.isOpen) _pulse.repeat(reverse: true);
  }

  @override
  void didUpdateWidget(OpenStatusToggle old) {
    super.didUpdateWidget(old);
    if (widget.status.isOpen && !_pulse.isAnimating) {
      _pulse.repeat(reverse: true);
    } else if (!widget.status.isOpen) {
      _pulse.stop();
      _pulse.reset();
    }
  }

  @override
  void dispose() {
    _pulse.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isOpen = widget.status.isOpen;

    return GestureDetector(
      onTap: _isLoading ? null : () => _handleToggle(context),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 300),
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.md,
          vertical: AppSpacing.sm,
        ),
        decoration: BoxDecoration(
          color: isOpen
              ? AppColors.confirmed.withOpacity(0.12)
              : AppColors.graphite,
          borderRadius: AppRadius.rPill,
          border: Border.all(
            color: isOpen
                ? AppColors.confirmed.withOpacity(0.4)
                : AppColors.smoke,
          ),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Dot animado
            AnimatedBuilder(
              animation: _pulse,
              builder: (_, __) => Container(
                width: 8,
                height: 8,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: isOpen ? AppColors.confirmed : AppColors.ash,
                  boxShadow: isOpen
                      ? [
                          BoxShadow(
                            color: AppColors.confirmed
                                .withOpacity(0.3 + 0.3 * _pulse.value),
                            blurRadius: 8,
                            spreadRadius: 2,
                          ),
                        ]
                      : null,
                ),
              ),
            ),
            const SizedBox(width: AppSpacing.sm),
            if (_isLoading)
              const SizedBox(
                width: 12,
                height: 12,
                child: CircularProgressIndicator(
                  strokeWidth: 1.5,
                  color: AppColors.silver,
                ),
              )
            else
              Text(
                isOpen ? 'ABIERTO' : 'CERRADO',
                style: AppTypography.overline.copyWith(
                  color: isOpen ? AppColors.confirmed : AppColors.silver,
                  letterSpacing: 0.2,
                  fontSize: 10,
                ),
              ),
            const SizedBox(width: AppSpacing.xs),
            Icon(
              Icons.keyboard_arrow_down_rounded,
              size: 14,
              color: isOpen ? AppColors.confirmed : AppColors.ash,
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _handleToggle(BuildContext context) async {
    final isOpen = widget.status.isOpen;

    if (isOpen) {
      // Cerrar → pedir razón opcional
      final result = await _showCloseDialog(context);
      if (result == null) return;
      setState(() => _isLoading = true);
      await widget.onToggle(false, result.isEmpty ? null : result);
    } else {
      // Abrir → confirmación rápida
      HapticFeedback.mediumImpact();
      setState(() => _isLoading = true);
      await widget.onToggle(true, null);
    }
    if (mounted) setState(() => _isLoading = false);
  }

  Future<String?> _showCloseDialog(BuildContext context) async {
    final controller = TextEditingController();
    return showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.graphite,
        shape: RoundedRectangleBorder(borderRadius: AppRadius.rModal),
        title: Text('Cerrar barbería', style: AppTypography.headingMd),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Los clientes verán que estás cerrado.',
              style: AppTypography.bodyMd,
            ),
            const SizedBox(height: AppSpacing.md),
            TextField(
              controller: controller,
              style: AppTypography.bodyMd.copyWith(color: AppColors.white),
              decoration: InputDecoration(
                hintText: 'Razón (opcional): "Día festivo"...',
                hintStyle: AppTypography.bodyMd.copyWith(color: AppColors.ash),
                filled: true,
                fillColor: AppColors.slate,
                border: OutlineInputBorder(
                  borderRadius: AppRadius.rInput,
                  borderSide: const BorderSide(color: AppColors.smoke),
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: AppRadius.rInput,
                  borderSide: const BorderSide(color: AppColors.smoke),
                ),
                focusedBorder: OutlineInputBorder(
                  borderRadius: AppRadius.rInput,
                  borderSide: const BorderSide(color: AppColors.gold),
                ),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text('Cancelar', style: AppTypography.labelMd.copyWith(
              color: AppColors.silver,
            )),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, controller.text),
            child: Text('Cerrar barbería', style: AppTypography.labelMd.copyWith(
              color: AppColors.error,
              fontWeight: FontWeight.w700,
            )),
          ),
        ],
      ),
    );
  }
}

// ─── ServicesGrid ─────────────────────────────────────────────────────────────
// Servicios en formato grid (no lista)

class ServicesGrid extends StatelessWidget {
  final List<ServiceGridItem> services;
  final VoidCallback? onAddService;

  const ServicesGrid({
    super.key,
    required this.services,
    this.onAddService,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text('SERVICIOS', style: AppTypography.overline.copyWith(
              color: AppColors.ash,
            )),
            const Spacer(),
            GestureDetector(
              onTap: onAddService,
              child: Text('+ Agregar', style: AppTypography.labelSm.copyWith(
                color: AppColors.gold,
              )),
            ),
          ],
        ),
        const SizedBox(height: AppSpacing.sm),
        GridView.builder(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: 2,
            crossAxisSpacing: AppSpacing.sm,
            mainAxisSpacing: AppSpacing.sm,
            childAspectRatio: 1.6,
          ),
          itemCount: services.length,
          itemBuilder: (context, i) => _ServiceCard(service: services[i])
              .animate()
              .fadeIn(delay: Duration(milliseconds: 50 * i))
              .scale(begin: const Offset(0.95, 0.95)),
        ),
      ],
    );
  }
}

class ServiceGridItem {
  final String id;
  final String name;
  final String category;
  final String price;
  final int durationMinutes;
  final bool hasDeposit;

  const ServiceGridItem({
    required this.id,
    required this.name,
    required this.category,
    required this.price,
    required this.durationMinutes,
    required this.hasDeposit,
  });

  String get categoryEmoji => switch (category.toUpperCase()) {
    'CORTE'       => '✂️',
    'BARBA'       => '🪒',
    'CEJA'        => '✨',
    'TRATAMIENTO' => '💆',
    'COMBO'       => '💈',
    _             => '💇',
  };
}

class _ServiceCard extends StatelessWidget {
  final ServiceGridItem service;

  const _ServiceCard({required this.service});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: AppColors.graphite,
        borderRadius: AppRadius.rCard,
        border: Border.all(color: AppColors.smoke),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(service.categoryEmoji, style: const TextStyle(fontSize: 16)),
              const Spacer(),
              if (service.hasDeposit)
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 5, vertical: 1,
                  ),
                  decoration: BoxDecoration(
                    color: AppColors.goldSubtle,
                    borderRadius: AppRadius.rPill,
                  ),
                  child: Text('Depósito', style: AppTypography.overline.copyWith(
                    color: AppColors.gold,
                    fontSize: 7,
                  )),
                ),
            ],
          ),
          const Spacer(),
          Text(
            service.name,
            style: AppTypography.labelLg.copyWith(fontSize: 12),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 2),
          Row(
            children: [
              Text(
                '\$${service.price}',
                style: AppTypography.monoSm.copyWith(
                  color: AppColors.gold,
                  fontSize: 12,
                ),
              ),
              const Spacer(),
              Text(
                '${service.durationMinutes}m',
                style: AppTypography.overline.copyWith(
                  color: AppColors.ash,
                  fontSize: 9,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

// ─── LoyaltySection ───────────────────────────────────────────────────────────
// Sección completa de fidelización para el home del owner

class LoyaltySection extends StatelessWidget {
  final List<LoyaltyProgram> programs;
  final String qrToken;
  final String tenantId;
  final VoidCallback onManagePrograms;
  final VoidCallback onShowQr;

  const LoyaltySection({
    super.key,
    required this.programs,
    required this.qrToken,
    required this.tenantId,
    required this.onManagePrograms,
    required this.onShowQr,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Header
        Row(
          children: [
            Text('FIDELIZACIÓN', style: AppTypography.overline.copyWith(
              color: AppColors.ash,
            )),
            const Spacer(),
            GestureDetector(
              onTap: onManagePrograms,
              child: Text('Gestionar', style: AppTypography.labelSm.copyWith(
                color: AppColors.gold,
              )),
            ),
          ],
        ),
        const SizedBox(height: AppSpacing.sm),

        // QR Card
        _QrCard(
          qrToken: qrToken,
          tenantId: tenantId,
          onTap: onShowQr,
        ).animate().fadeIn(delay: 100.ms),

        const SizedBox(height: AppSpacing.sm),

        // Programas activos
        if (programs.isEmpty)
          _EmptyProgramsCard(onAdd: onManagePrograms)
        else
          ...programs.map((p) => Padding(
            padding: const EdgeInsets.only(bottom: AppSpacing.sm),
            child: _ProgramCard(program: p),
          )).toList(),
      ],
    );
  }
}

class _QrCard extends StatelessWidget {
  final String qrToken;
  final String tenantId;
  final VoidCallback onTap;

  const _QrCard({
    required this.qrToken,
    required this.tenantId,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: GoldGlowContainer(
        child: Row(
          children: [
            // QR placeholder visual
            Container(
              width: 56,
              height: 56,
              decoration: BoxDecoration(
                color: AppColors.white,
                borderRadius: AppRadius.rSm,
              ),
              child: Stack(
                alignment: Alignment.center,
                children: [
                  // Simulación visual de QR
                  GridView.builder(
                    physics: const NeverScrollableScrollPhysics(),
                    padding: const EdgeInsets.all(6),
                    gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                      crossAxisCount: 5,
                      crossAxisSpacing: 1,
                      mainAxisSpacing: 1,
                    ),
                    itemCount: 25,
                    itemBuilder: (_, i) => Container(
                      decoration: BoxDecoration(
                        color: [0, 1, 5, 6, 7, 10, 12, 14, 17, 18, 19, 23, 24]
                                .contains(i)
                            ? AppColors.obsidian
                            : Colors.transparent,
                        borderRadius: BorderRadius.circular(1),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('QR de tu barbería', style: AppTypography.headingSm),
                  const SizedBox(height: 2),
                  Text(
                    'Muéstraselo a tus clientes para que te agreguen como favorito y accedan a tus beneficios',
                    style: AppTypography.bodySm.copyWith(fontSize: 11),
                  ),
                ],
              ),
            ),
            const SizedBox(width: AppSpacing.sm),
            Container(
              padding: const EdgeInsets.all(AppSpacing.sm),
              decoration: BoxDecoration(
                color: AppColors.gold.withOpacity(0.15),
                borderRadius: AppRadius.rSm,
              ),
              child: const Icon(
                Icons.qr_code_rounded,
                color: AppColors.gold,
                size: 20,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ProgramCard extends StatelessWidget {
  final LoyaltyProgram program;

  const _ProgramCard({required this.program});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: AppColors.graphite,
        borderRadius: AppRadius.rCard,
        border: Border.all(color: AppColors.smoke),
      ),
      child: Row(
        children: [
          // Emoji
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: AppColors.slate,
              borderRadius: AppRadius.rSm,
            ),
            child: Center(
              child: Text(program.emoji, style: const TextStyle(fontSize: 18)),
            ),
          ),
          const SizedBox(width: AppSpacing.md),
          // Info
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(program.title, style: AppTypography.labelLg),
                const SizedBox(height: 2),
                Text(
                  program.conditionText,
                  style: AppTypography.bodySm,
                ),
              ],
            ),
          ),
          // Reward badge
          Container(
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.sm,
              vertical: AppSpacing.xs,
            ),
            decoration: BoxDecoration(
              color: AppColors.goldSubtle,
              borderRadius: AppRadius.rPill,
              border: Border.all(color: AppColors.gold.withOpacity(0.3)),
            ),
            child: Text(
              program.rewardDescription,
              style: AppTypography.monoSm.copyWith(
                color: AppColors.gold,
                fontSize: 10,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _EmptyProgramsCard extends StatelessWidget {
  final VoidCallback onAdd;

  const _EmptyProgramsCard({required this.onAdd});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onAdd,
      child: Container(
        padding: const EdgeInsets.all(AppSpacing.base),
        decoration: BoxDecoration(
          color: AppColors.graphite,
          borderRadius: AppRadius.rCard,
          border: Border.all(
            color: AppColors.smoke,
            style: BorderStyle.solid,
          ),
        ),
        child: Row(
          children: [
            const Icon(Icons.add_circle_outline_rounded,
              color: AppColors.ash, size: 20),
            const SizedBox(width: AppSpacing.md),
            Text(
              'Agregar programa de fidelización',
              style: AppTypography.bodyMd.copyWith(color: AppColors.silver),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── QR Full Screen Modal ─────────────────────────────────────────────────────

class QrFullScreenModal extends StatelessWidget {
  final String qrToken;
  final String tenantId;
  final String tenantName;

  const QrFullScreenModal({
    super.key,
    required this.qrToken,
    required this.tenantId,
    required this.tenantName,
  });

  static void show(
    BuildContext context, {
    required String qrToken,
    required String tenantId,
    required String tenantName,
  }) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => QrFullScreenModal(
        qrToken: qrToken,
        tenantId: tenantId,
        tenantName: tenantName,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      height: MediaQuery.of(context).size.height * 0.85,
      decoration: const BoxDecoration(
        color: AppColors.graphite,
        borderRadius: AppRadius.rBottomSheet,
      ),
      child: Column(
        children: [
          // Handle
          Container(
            margin: const EdgeInsets.only(top: AppSpacing.md),
            width: 40, height: 4,
            decoration: BoxDecoration(
              color: AppColors.smoke, borderRadius: AppRadius.rPill,
            ),
          ),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.all(AppSpacing.base),
              child: Column(
                children: [
                  const SizedBox(height: AppSpacing.md),
                  Text(
                    tenantName,
                    style: AppTypography.displayMd,
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: AppSpacing.xs),
                  Text(
                    'Escanea para ser cliente favorito',
                    style: AppTypography.bodyMd.copyWith(color: AppColors.silver),
                    textAlign: TextAlign.center,
                  ),

                  const Spacer(),

                  // QR grande — en producción usar qr_flutter package
                  Container(
                    width: 240,
                    height: 240,
                    decoration: BoxDecoration(
                      color: AppColors.white,
                      borderRadius: AppRadius.rCard,
                      boxShadow: AppColors.shadowGold,
                    ),
                    child: Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          // TODO: reemplazar con QrImageView del package qr_flutter
                          Icon(
                            Icons.qr_code_2_rounded,
                            size: 160,
                            color: AppColors.obsidian,
                          ),
                          Text(
                            'barberia://join/$tenantId/$qrToken',
                            style: TextStyle(
                              fontSize: 6,
                              color: AppColors.ash,
                              fontFamily: 'DMMono',
                            ),
                            textAlign: TextAlign.center,
                          ),
                        ],
                      ),
                    ),
                  ),

                  const Spacer(),

                  // Instrucción
                  Container(
                    padding: const EdgeInsets.all(AppSpacing.md),
                    decoration: BoxDecoration(
                      color: AppColors.slate,
                      borderRadius: AppRadius.rCard,
                      border: Border.all(color: AppColors.smoke),
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.info_outline_rounded,
                          size: 16, color: AppColors.ash),
                        const SizedBox(width: AppSpacing.sm),
                        Expanded(
                          child: Text(
                            'El cliente escanea con la app y queda registrado como tu cliente fijo. Sus visitas se cuentan automáticamente.',
                            style: AppTypography.bodySm,
                          ),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: AppSpacing.xl),

                  // Botón compartir link
                  GestureDetector(
                    onTap: () {
                      Clipboard.setData(ClipboardData(
                        text: 'barberia://join/$tenantId/$qrToken',
                      ));
                      HapticFeedback.lightImpact();
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text('Link copiado',
                            style: AppTypography.bodyMd.copyWith(
                              color: AppColors.white,
                            )),
                          backgroundColor: AppColors.slate,
                          behavior: SnackBarBehavior.floating,
                          shape: RoundedRectangleBorder(
                            borderRadius: AppRadius.rMd,
                          ),
                          duration: const Duration(seconds: 1),
                        ),
                      );
                    },
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: AppSpacing.xl,
                        vertical: AppSpacing.md,
                      ),
                      decoration: BoxDecoration(
                        color: AppColors.graphite,
                        borderRadius: AppRadius.rButton,
                        border: Border.all(color: AppColors.smoke),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.link_rounded,
                            size: 16, color: AppColors.silver),
                          const SizedBox(width: AppSpacing.sm),
                          Text('Copiar link', style: AppTypography.labelLg.copyWith(
                            color: AppColors.silver,
                          )),
                        ],
                      ),
                    ),
                  ),

                  const SizedBox(height: AppSpacing.xxl),
                ],
              ),
            ),
          ),
        ],
      ),
    ).animate().slideY(begin: 0.2, duration: 350.ms, curve: Curves.easeOutCubic);
  }
}
