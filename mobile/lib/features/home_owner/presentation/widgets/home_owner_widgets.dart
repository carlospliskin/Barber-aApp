import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:qr_flutter/qr_flutter.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../shared/widgets/app_components.dart';
import '../../../../shared/widgets/app_button.dart';
import '../../domain/entities/loyalty_entities.dart';
import '../notifiers/home_owner_notifier.dart';

// ─── OpenStatusBadge — estado ABIERTO/CERRADO con toggle ─────────────────────

class OpenStatusWidget extends ConsumerWidget {
  const OpenStatusWidget({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final status   = ref.watch(homeOwnerNotifierProvider.select((s) => s.openStatus));
    final toggling = ref.watch(homeOwnerNotifierProvider.select((s) => s.togglingStatus));
    final isOpen   = status?.isOpen ?? false;

    return GestureDetector(
      onTap: toggling
          ? null
          : () {
              HapticFeedback.mediumImpact();
              ref.read(homeOwnerNotifierProvider.notifier).toggleOpenStatus();
            },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 280),
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.md,
          vertical: AppSpacing.xs + 2,
        ),
        decoration: BoxDecoration(
          color: isOpen
              ? AppColors.confirmed.withOpacity(0.12)
              : AppColors.error.withOpacity(0.12),
          borderRadius: AppRadius.rPill,
          border: Border.all(
            color: isOpen
                ? AppColors.confirmed.withOpacity(0.4)
                : AppColors.error.withOpacity(0.4),
          ),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (toggling)
              SizedBox(
                width: 8, height: 8,
                child: CircularProgressIndicator(
                  strokeWidth: 1.5,
                  color: isOpen ? AppColors.confirmed : AppColors.error,
                ),
              )
            else
              // Dot parpadeante si está abierto
              _PulsingDot(color: isOpen ? AppColors.confirmed : AppColors.error),
            const SizedBox(width: 6),
            Text(
              isOpen ? 'ABIERTO' : 'CERRADO',
              style: AppTypography.overline.copyWith(
                color: isOpen ? AppColors.confirmed : AppColors.error,
                fontWeight: FontWeight.w700,
                fontSize: 10,
              ),
            ),
            const SizedBox(width: 6),
            Icon(
              isOpen
                  ? Icons.toggle_on_rounded
                  : Icons.toggle_off_rounded,
              size: 16,
              color: isOpen ? AppColors.confirmed : AppColors.ash,
            ),
          ],
        ),
      ),
    );
  }
}

class _PulsingDot extends StatefulWidget {
  final Color color;
  const _PulsingDot({required this.color});

  @override
  State<_PulsingDot> createState() => _PulsingDotState();
}

class _PulsingDotState extends State<_PulsingDot>
    with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;
  late Animation<double> _anim;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 1),
    )..repeat(reverse: true);
    _anim = Tween<double>(begin: 0.4, end: 1.0).animate(_ctrl);
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _anim,
      builder: (_, __) => Container(
        width: 7, height: 7,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: widget.color.withOpacity(_anim.value),
        ),
      ),
    );
  }
}

// ─── ServicesGrid — Grid 2 columnas ──────────────────────────────────────────

class ServicesGrid extends StatelessWidget {
  final List<OwnerService> services;
  final bool isLoading;
  final VoidCallback? onAddService;

  const ServicesGrid({
    super.key,
    required this.services,
    this.isLoading = false,
    this.onAddService,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _SectionHeader(
          title: 'SERVICIOS',
          trailing: GestureDetector(
            onTap: onAddService,
            child: Text(
              'Gestionar →',
              style: AppTypography.labelSm.copyWith(color: AppColors.gold),
            ),
          ),
        ),
        const SizedBox(height: AppSpacing.sm),
        if (isLoading)
          _ServicesGridSkeleton()
        else if (services.isEmpty)
          _EmptyServices(onAdd: onAddService)
        else
          GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 2,
              crossAxisSpacing: AppSpacing.sm,
              mainAxisSpacing: AppSpacing.sm,
              childAspectRatio: 1.6,
            ),
            itemCount: services.length + 1, // +1 para el botón "Agregar"
            itemBuilder: (_, i) {
              if (i == services.length) {
                return _AddServiceCard(onTap: onAddService);
              }
              return _ServiceCard(service: services[i])
                  .animate()
                  .fadeIn(delay: Duration(milliseconds: 60 * i));
            },
          ),
      ],
    );
  }
}

class _ServiceCard extends StatelessWidget {
  final OwnerService service;
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
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          // Categoría
          Container(
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.xs + 2,
              vertical: 2,
            ),
            decoration: BoxDecoration(
              color: AppColors.goldSubtle,
              borderRadius: AppRadius.rPill,
            ),
            child: Text(
              service.category,
              style: AppTypography.overline.copyWith(
                color: AppColors.gold,
                fontSize: 8,
              ),
            ),
          ),
          // Nombre
          Text(
            service.name,
            style: AppTypography.labelLg.copyWith(fontSize: 12),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          // Precio y duración
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                service.priceLabel,
                style: AppTypography.monoMd.copyWith(fontSize: 13),
              ),
              Text(
                service.durationLabel,
                style: AppTypography.monoSm.copyWith(fontSize: 10),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _AddServiceCard extends StatelessWidget {
  final VoidCallback? onTap;
  const _AddServiceCard({this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        decoration: BoxDecoration(
          color: Colors.transparent,
          borderRadius: AppRadius.rCard,
          border: Border.all(color: AppColors.smoke, style: BorderStyle.solid),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.add_rounded, color: AppColors.ash, size: 20),
            const SizedBox(height: 4),
            Text(
              'Agregar',
              style: AppTypography.labelSm.copyWith(color: AppColors.ash),
            ),
          ],
        ),
      ),
    );
  }
}

class _EmptyServices extends StatelessWidget {
  final VoidCallback? onAdd;
  const _EmptyServices({this.onAdd});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onAdd,
      child: Container(
        height: 80,
        decoration: BoxDecoration(
          color: AppColors.graphite,
          borderRadius: AppRadius.rCard,
          border: Border.all(color: AppColors.smoke),
        ),
        child: Center(
          child: Text(
            '+ Agrega tu primer servicio',
            style: AppTypography.labelMd.copyWith(color: AppColors.ash),
          ),
        ),
      ),
    );
  }
}

class _ServicesGridSkeleton extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        crossAxisSpacing: AppSpacing.sm,
        mainAxisSpacing: AppSpacing.sm,
        childAspectRatio: 1.6,
      ),
      itemCount: 4,
      itemBuilder: (_, i) => Container(
        decoration: BoxDecoration(
          color: AppColors.graphite,
          borderRadius: AppRadius.rCard,
        ),
      ).animate(onPlay: (c) => c.repeat())
        .shimmer(duration: 1200.ms, color: AppColors.slate),
    );
  }
}

// ─── BarbersScheduleWidget — horario de hoy ───────────────────────────────────

class BarbersScheduleWidget extends StatelessWidget {
  final List<BarberWithSchedule> barbers;

  const BarbersScheduleWidget({super.key, required this.barbers});

  @override
  Widget build(BuildContext context) {
    if (barbers.isEmpty) return const SizedBox.shrink();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _SectionHeader(title: 'HORARIO HOY'),
        const SizedBox(height: AppSpacing.sm),
        ...barbers.asMap().entries.map((e) =>
          _BarberScheduleRow(barber: e.value)
              .animate()
              .fadeIn(delay: Duration(milliseconds: 80 * e.key)),
        ),
      ],
    );
  }
}

class _BarberScheduleRow extends StatelessWidget {
  final BarberWithSchedule barber;
  const _BarberScheduleRow({required this.barber});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: AppSpacing.sm),
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.md,
        vertical: AppSpacing.sm + 2,
      ),
      decoration: BoxDecoration(
        color: AppColors.graphite,
        borderRadius: AppRadius.rCard,
        border: Border.all(color: AppColors.smoke),
      ),
      child: Row(
        children: [
          // Avatar
          Container(
            width: 32, height: 32,
            decoration: BoxDecoration(
              color: barber.isWorkingToday
                  ? AppColors.goldSubtle
                  : AppColors.slate,
              shape: BoxShape.circle,
            ),
            child: Center(
              child: Text(
                barber.name.isNotEmpty ? barber.name[0].toUpperCase() : 'B',
                style: AppTypography.labelMd.copyWith(
                  color: barber.isWorkingToday
                      ? AppColors.gold
                      : AppColors.ash,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ),
          const SizedBox(width: AppSpacing.md),
          // Nombre
          Expanded(
            child: Text(
              barber.name,
              style: AppTypography.labelLg.copyWith(
                color: barber.isWorkingToday
                    ? AppColors.white
                    : AppColors.ash,
              ),
            ),
          ),
          // Horario
          Text(
            barber.scheduleLabel,
            style: AppTypography.monoSm.copyWith(
              color: barber.isWorkingToday
                  ? AppColors.gold
                  : AppColors.ash,
              fontSize: 11,
            ),
          ),
          // Capacity
          if (barber.isWorkingToday && barber.capacity > 1) ...[
            const SizedBox(width: AppSpacing.sm),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
              decoration: BoxDecoration(
                color: AppColors.slate,
                borderRadius: AppRadius.rPill,
              ),
              child: Text(
                'x${barber.capacity}',
                style: AppTypography.monoSm.copyWith(fontSize: 9),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

// ─── LoyaltySection — Fidelización con QR ────────────────────────────────────

class LoyaltySection extends StatelessWidget {
  final LoyaltyProgram? program;
  final bool isLoading;
  final String tenantId;
  final VoidCallback? onManageRules;

  const LoyaltySection({
    super.key,
    required this.program,
    required this.tenantId,
    this.isLoading = false,
    this.onManageRules,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _SectionHeader(
          title: 'FIDELIZACIÓN',
          trailing: program != null
              ? GestureDetector(
                  onTap: onManageRules,
                  child: Text(
                    'Gestionar →',
                    style: AppTypography.labelSm.copyWith(color: AppColors.gold),
                  ),
                )
              : null,
        ),
        const SizedBox(height: AppSpacing.sm),
        if (isLoading)
          _LoyaltySkeleton()
        else if (program == null)
          _NoLoyaltyProgram(onSetup: onManageRules)
        else
          _LoyaltyCard(program: program!, tenantId: tenantId),
      ],
    );
  }
}

class _LoyaltyCard extends StatelessWidget {
  final LoyaltyProgram program;
  final String tenantId;

  const _LoyaltyCard({required this.program, required this.tenantId});

  @override
  Widget build(BuildContext context) {
    return GoldGlowContainer(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              // QR Button
              GestureDetector(
                onTap: () => _showQR(context),
                child: Container(
                  width: 52, height: 52,
                  decoration: BoxDecoration(
                    color: AppColors.white,
                    borderRadius: AppRadius.rSm,
                    boxShadow: AppColors.shadowGold,
                  ),
                  child: Center(
                    child: Icon(
                      Icons.qr_code_2_rounded,
                      size: 32,
                      color: AppColors.obsidian,
                    ),
                  ),
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      program.name,
                      style: AppTypography.headingSm,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Row(
                      children: [
                        Icon(Icons.people_outline_rounded,
                          size: 12, color: AppColors.ash),
                        const SizedBox(width: 4),
                        Text(
                          '${program.totalMembers} miembros',
                          style: AppTypography.bodySm,
                        ),
                        const SizedBox(width: AppSpacing.md),
                        Icon(Icons.star_outline_rounded,
                          size: 12, color: AppColors.gold),
                        const SizedBox(width: 4),
                        Text(
                          '${program.activeRules.length} reglas',
                          style: AppTypography.bodySm.copyWith(
                            color: AppColors.gold,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),

          if (program.activeRules.isNotEmpty) ...[
            const SizedBox(height: AppSpacing.md),
            Container(height: 1, color: AppColors.gold.withOpacity(0.2)),
            const SizedBox(height: AppSpacing.md),
            // Reglas activas
            ...program.activeRules.take(3).map((rule) =>
              Padding(
                padding: const EdgeInsets.only(bottom: AppSpacing.xs),
                child: Row(
                  children: [
                    Container(
                      width: 5, height: 5,
                      decoration: const BoxDecoration(
                        shape: BoxShape.circle,
                        color: AppColors.gold,
                      ),
                    ),
                    const SizedBox(width: AppSpacing.sm),
                    Expanded(
                      child: Text(
                        rule.shortDescription,
                        style: AppTypography.bodySm.copyWith(
                          color: AppColors.pearl,
                          fontSize: 11,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            if (program.activeRules.length > 3)
              Text(
                '+${program.activeRules.length - 3} reglas más',
                style: AppTypography.labelSm.copyWith(color: AppColors.ash),
              ),
          ],

          const SizedBox(height: AppSpacing.md),
          // CTA mostrar QR
          GestureDetector(
            onTap: () => _showQR(context),
            child: Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(vertical: AppSpacing.sm),
              decoration: BoxDecoration(
                gradient: AppColors.goldGradient,
                borderRadius: AppRadius.rSm,
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.qr_code_rounded,
                    size: 14, color: AppColors.obsidian),
                  const SizedBox(width: AppSpacing.xs),
                  Text(
                    'Mostrar QR a tus clientes',
                    style: AppTypography.labelLg.copyWith(
                      color: AppColors.obsidian,
                      fontWeight: FontWeight.w700,
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _showQR(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (_) => _QRBottomSheet(
        tenantId: tenantId,
        programId: program.id,
        programName: program.name,
      ),
    );
  }
}

// ─── QR Bottom Sheet ──────────────────────────────────────────────────────────

class _QRBottomSheet extends StatelessWidget {
  final String tenantId;
  final String programId;
  final String programName;

  const _QRBottomSheet({
    required this.tenantId,
    required this.programId,
    required this.programName,
  });

  @override
  Widget build(BuildContext context) {
    // El deeplink que va en el QR
    final deeplink = 'barberia://loyalty/$tenantId/$programId';

    return Container(
      decoration: const BoxDecoration(
        color: AppColors.graphite,
        borderRadius: AppRadius.rBottomSheet,
      ),
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).padding.bottom + AppSpacing.lg,
        top: AppSpacing.lg,
        left: AppSpacing.base,
        right: AppSpacing.base,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 40, height: 4,
            margin: const EdgeInsets.only(bottom: AppSpacing.xl),
            decoration: BoxDecoration(
              color: AppColors.smoke, borderRadius: AppRadius.rPill,
            ),
          ),
          Text(
            programName,
            style: AppTypography.displayMd.copyWith(fontSize: 20),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: AppSpacing.xs),
          Text(
            'Pídele a tus clientes que escaneen este QR\npara unirse a tu programa de fidelización',
            style: AppTypography.bodySm,
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: AppSpacing.xxl),

          // QR real generado por qr_flutter
          Container(
            width: 200, height: 200,
            padding: const EdgeInsets.all(AppSpacing.md),
            decoration: BoxDecoration(
              color: AppColors.white,
              borderRadius: AppRadius.rMd,
              boxShadow: AppColors.shadowGold,
            ),
            child: QrImageView(
              data: deeplink,
              version: QrVersions.auto,
              size: 168,
              backgroundColor: AppColors.white,
              eyeStyle: const QrEyeStyle(
                eyeShape: QrEyeShape.square,
                color: AppColors.obsidian,
              ),
              dataModuleStyle: const QrDataModuleStyle(
                dataModuleShape: QrDataModuleShape.square,
                color: AppColors.obsidian,
              ),
            ),
          ).animate().scale(
            begin: const Offset(0.85, 0.85),
            curve: Curves.easeOutBack,
          ),

          const SizedBox(height: AppSpacing.lg),

          // Deeplink
          GestureDetector(
            onTap: () {
              Clipboard.setData(ClipboardData(text: deeplink));
              HapticFeedback.lightImpact();
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text('Link copiado',
                    style: AppTypography.bodyMd.copyWith(color: AppColors.white)),
                  backgroundColor: AppColors.slate,
                  behavior: SnackBarBehavior.floating,
                  duration: const Duration(seconds: 1),
                ),
              );
            },
            child: Container(
              padding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.md, vertical: AppSpacing.sm,
              ),
              decoration: BoxDecoration(
                color: AppColors.slate,
                borderRadius: AppRadius.rPill,
                border: Border.all(color: AppColors.smoke),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.link_rounded, size: 13, color: AppColors.ash),
                  const SizedBox(width: AppSpacing.xs),
                  Text(
                    deeplink,
                    style: AppTypography.monoSm.copyWith(fontSize: 10),
                  ),
                  const SizedBox(width: AppSpacing.xs),
                  const Icon(Icons.copy_rounded, size: 12, color: AppColors.ash),
                ],
              ),
            ),
          ),

          const SizedBox(height: AppSpacing.md),
          Text(
            'Los clientes acumulan visitas automáticamente\nal completar sus citas',
            style: AppTypography.bodySm.copyWith(color: AppColors.ash),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    ).animate().slideY(begin: 0.3, duration: 350.ms, curve: Curves.easeOutCubic);
  }
}

class _NoLoyaltyProgram extends StatelessWidget {
  final VoidCallback? onSetup;
  const _NoLoyaltyProgram({this.onSetup});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onSetup,
      child: Container(
        padding: const EdgeInsets.all(AppSpacing.base),
        decoration: BoxDecoration(
          color: AppColors.graphite,
          borderRadius: AppRadius.rCard,
          border: Border.all(color: AppColors.smoke),
        ),
        child: Row(
          children: [
            Container(
              width: 40, height: 40,
              decoration: BoxDecoration(
                color: AppColors.slate,
                borderRadius: AppRadius.rSm,
              ),
              child: const Icon(
                Icons.card_giftcard_rounded,
                color: AppColors.ash, size: 20,
              ),
            ),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Configura tu programa de lealtad',
                    style: AppTypography.labelLg.copyWith(color: AppColors.white)),
                  Text('Retén a tus clientes con beneficios exclusivos',
                    style: AppTypography.bodySm),
                ],
              ),
            ),
            const Icon(Icons.chevron_right_rounded,
              color: AppColors.ash, size: 18),
          ],
        ),
      ),
    );
  }
}

class _LoyaltySkeleton extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      height: 100,
      decoration: BoxDecoration(
        color: AppColors.graphite, borderRadius: AppRadius.rCard,
      ),
    ).animate(onPlay: (c) => c.repeat())
      .shimmer(duration: 1200.ms, color: AppColors.slate);
  }
}

// ─── Shared widgets ───────────────────────────────────────────────────────────

class _SectionHeader extends StatelessWidget {
  final String title;
  final Widget? trailing;

  const _SectionHeader({required this.title, this.trailing});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Text(
          title,
          style: AppTypography.overline.copyWith(color: AppColors.ash),
        ),
        if (trailing != null) ...[
          const Spacer(),
          trailing!,
        ],
      ],
    );
  }
}
