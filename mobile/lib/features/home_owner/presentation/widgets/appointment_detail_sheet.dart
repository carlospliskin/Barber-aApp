import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../shared/widgets/app_button.dart';
import '../../../../shared/widgets/app_components.dart';
import '../../domain/entities/dashboard_entities.dart';
import '../notifiers/home_owner_notifier.dart';

/// Muestra el detalle completo de una cita en un bottom sheet.
/// Se abre desde AppointmentCard.onTap
void showAppointmentDetail(
  BuildContext context,
  WidgetRef ref,
  Appointment appointment,
) {
  showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
    builder: (_) => ProviderScope(
      parent: ProviderScope.containerOf(context),
      child: _AppointmentDetailSheet(appointment: appointment),
    ),
  );
}

class _AppointmentDetailSheet extends ConsumerStatefulWidget {
  final Appointment appointment;
  const _AppointmentDetailSheet({required this.appointment});

  @override
  ConsumerState<_AppointmentDetailSheet> createState() =>
      _AppointmentDetailSheetState();
}

class _AppointmentDetailSheetState
    extends ConsumerState<_AppointmentDetailSheet> {
  late Appointment _apt;
  bool _isMarkingNoShow = false;

  @override
  void initState() {
    super.initState();
    _apt = widget.appointment;
  }

  @override
  Widget build(BuildContext context) {
    final bottomPadding = MediaQuery.of(context).viewInsets.bottom +
        MediaQuery.of(context).padding.bottom;

    return Container(
      decoration: const BoxDecoration(
        color: AppColors.graphite,
        borderRadius: AppRadius.rBottomSheet,
      ),
      padding: EdgeInsets.only(bottom: bottomPadding),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Drag handle
          Container(
            margin: const EdgeInsets.only(top: AppSpacing.md),
            width: 40,
            height: 4,
            decoration: BoxDecoration(
              color: AppColors.smoke,
              borderRadius: AppRadius.rPill,
            ),
          ),
          Flexible(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(AppSpacing.base),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildHeader(),
                  const SizedBox(height: AppSpacing.lg),
                  _buildClientSection(),
                  _buildDivider(),
                  _buildServiceSection(),
                  _buildDivider(),
                  _buildStatusSection(),
                  if (_apt.depositAmount != null) ...[
                    _buildDivider(),
                    _buildDepositSection(),
                  ],
                  const SizedBox(height: AppSpacing.lg),
                  _buildActions(),
                  const SizedBox(height: AppSpacing.md),
                ],
              ),
            ),
          ),
        ],
      ),
    ).animate().slideY(
      begin: 0.3,
      duration: 350.ms,
      curve: Curves.easeOutCubic,
    );
  }

  // ─── Header ────────────────────────────────────────────────────────────────

  Widget _buildHeader() {
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                _apt.client.name,
                style: AppTypography.displayMd.copyWith(fontSize: 22),
              ),
              const SizedBox(height: 4),
              Row(
                children: [
                  Icon(
                    Icons.access_time_rounded,
                    size: 13,
                    color: AppColors.ash,
                  ),
                  const SizedBox(width: 4),
                  Text(
                    _apt.timeRange,
                    style: AppTypography.monoMd.copyWith(
                      color: AppColors.gold,
                    ),
                  ),
                  const SizedBox(width: AppSpacing.sm),
                  Text('·', style: AppTypography.bodySm),
                  const SizedBox(width: AppSpacing.sm),
                  Text(
                    _formatDate(_apt.startTime),
                    style: AppTypography.bodySm,
                  ),
                ],
              ),
            ],
          ),
        ),
        AppBadge(
          label: _apt.status.label,
          variant: _statusToBadge(_apt.status),
        ),
      ],
    );
  }

  // ─── Cliente ───────────────────────────────────────────────────────────────

  Widget _buildClientSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _sectionLabel('CLIENTE'),
        const SizedBox(height: AppSpacing.sm),
        Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: AppColors.slate,
                shape: BoxShape.circle,
                border: Border.all(color: AppColors.smoke),
              ),
              child: Center(
                child: Text(
                  _apt.client.name.isNotEmpty
                      ? _apt.client.name[0].toUpperCase()
                      : 'C',
                  style: AppTypography.headingMd.copyWith(
                    color: AppColors.pearl,
                    fontSize: 16,
                  ),
                ),
              ),
            ),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(_apt.client.name, style: AppTypography.headingSm),
                  if (_apt.client.phone.isNotEmpty)
                    Text(
                      _apt.client.phone,
                      style: AppTypography.bodySm,
                    ),
                  if (_apt.client.email != null)
                    Text(
                      _apt.client.email!,
                      style: AppTypography.bodySm,
                    ),
                ],
              ),
            ),
            // Botón llamar
            GestureDetector(
              onTap: () => HapticFeedback.lightImpact(),
              child: Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: AppColors.goldSubtle,
                  borderRadius: AppRadius.rSm,
                  border: Border.all(
                    color: AppColors.gold.withOpacity(0.3),
                  ),
                ),
                child: const Icon(
                  Icons.phone_outlined,
                  size: 16,
                  color: AppColors.gold,
                ),
              ),
            ),
          ],
        ),
        if (_apt.client.notes != null &&
            _apt.client.notes!.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.sm),
          Container(
            padding: const EdgeInsets.all(AppSpacing.sm + 2),
            decoration: BoxDecoration(
              color: AppColors.slate,
              borderRadius: AppRadius.rSm,
              border: Border.all(color: AppColors.smoke),
            ),
            child: Row(
              children: [
                const Icon(
                  Icons.sticky_note_2_outlined,
                  size: 13,
                  color: AppColors.ash,
                ),
                const SizedBox(width: AppSpacing.sm),
                Expanded(
                  child: Text(
                    _apt.client.notes!,
                    style: AppTypography.bodySm,
                  ),
                ),
              ],
            ),
          ),
        ],
      ],
    );
  }

  // ─── Servicio ──────────────────────────────────────────────────────────────

  Widget _buildServiceSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _sectionLabel('SERVICIO'),
        const SizedBox(height: AppSpacing.sm),
        Row(
          children: [
            Container(
              padding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.sm,
                vertical: AppSpacing.xs,
              ),
              decoration: BoxDecoration(
                color: AppColors.goldSubtle,
                borderRadius: AppRadius.rSm,
              ),
              child: Text(
                _apt.service.category,
                style: AppTypography.labelSm.copyWith(
                  color: AppColors.gold,
                  fontSize: 9,
                ),
              ),
            ),
            const SizedBox(width: AppSpacing.sm),
            Expanded(
              child: Text(
                _apt.service.name,
                style: AppTypography.headingSm,
              ),
            ),
          ],
        ),
        const SizedBox(height: AppSpacing.sm),
        Row(
          children: [
            _infoChip(
              Icons.timer_outlined,
              '${_apt.service.durationMinutes} min',
            ),
            const SizedBox(width: AppSpacing.sm),
            _infoChip(
              Icons.attach_money_rounded,
              '\$${_apt.service.basePrice} MXN',
            ),
            const SizedBox(width: AppSpacing.sm),
            _infoChip(
              Icons.content_cut_rounded,
              _apt.barberName,
            ),
          ],
        ),
      ],
    );
  }

  // ─── Estado ────────────────────────────────────────────────────────────────

  Widget _buildStatusSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _sectionLabel('ESTADO'),
        const SizedBox(height: AppSpacing.sm),
        Row(
          children: [
            _dataRow('Confirmación', _apt.confirmationCode),
            const Spacer(),
            _dataRow(
              'Reagendado',
              '${_apt.rescheduleCount}/2 veces',
            ),
          ],
        ),
      ],
    );
  }

  // ─── Depósito ──────────────────────────────────────────────────────────────

  Widget _buildDepositSection() {
    final depositPaid = _apt.depositStatus == 'PAID';
    final depositRetained = _apt.depositStatus == 'RETAINED';

    return GoldGlowContainer(
      child: Row(
        children: [
          Icon(
            depositPaid
                ? Icons.check_circle_rounded
                : depositRetained
                    ? Icons.lock_rounded
                    : Icons.pending_rounded,
            size: 20,
            color: depositPaid
                ? AppColors.confirmed
                : depositRetained
                    ? AppColors.error
                    : AppColors.gold,
          ),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  depositPaid
                      ? 'Depósito pagado'
                      : depositRetained
                          ? 'Depósito retenido'
                          : 'Depósito pendiente',
                  style: AppTypography.labelLg.copyWith(
                    color: depositPaid ? AppColors.confirmed : AppColors.white,
                  ),
                ),
                if (_apt.depositAmount != null)
                  Text(
                    '\$${_apt.depositAmount} MXN',
                    style: AppTypography.monoMd,
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // ─── Acciones ──────────────────────────────────────────────────────────────

  Widget _buildActions() {
    if (!_apt.isActive) return const SizedBox.shrink();

    return Column(
      children: [
        if (_apt.isConfirmed) ...[
          // Marcar no-show
          AppButton(
            label: 'Marcar como no-show',
            variant: AppButtonVariant.danger,
            loading: _isMarkingNoShow,
            onTap: _isMarkingNoShow ? null : _handleMarkNoShow,
          ),
          const SizedBox(height: AppSpacing.sm),
        ],
        // Código de confirmación
        AppButton(
          label: 'Código: ${_apt.confirmationCode}',
          variant: AppButtonVariant.ghost,
          icon: Icons.copy_rounded,
          onTap: () {
            Clipboard.setData(ClipboardData(text: _apt.confirmationCode));
            HapticFeedback.lightImpact();
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(
                  'Código copiado',
                  style: AppTypography.bodyMd.copyWith(color: AppColors.white),
                ),
                backgroundColor: AppColors.slate,
                behavior: SnackBarBehavior.floating,
                shape: RoundedRectangleBorder(
                  borderRadius: AppRadius.rMd,
                ),
                duration: const Duration(seconds: 1),
              ),
            );
          },
        ),
      ],
    );
  }

  // ─── Handlers ──────────────────────────────────────────────────────────────

  Future<void> _handleMarkNoShow() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.graphite,
        shape: RoundedRectangleBorder(borderRadius: AppRadius.rModal),
        title: Text('Marcar no-show', style: AppTypography.headingMd),
        content: Text(
          '¿${_apt.client.name} no se presentó?\n\nEl depósito se retendrá si aplica.',
          style: AppTypography.bodyMd,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text(
              'Cancelar',
              style: AppTypography.labelMd.copyWith(color: AppColors.silver),
            ),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(
              'Confirmar',
              style: AppTypography.labelMd.copyWith(
                color: AppColors.error,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ],
      ),
    );

    if (confirm != true || !mounted) return;

    setState(() => _isMarkingNoShow = true);

    await ref
        .read(homeOwnerNotifierProvider.notifier)
        .markNoShow(_apt.id);

    if (mounted) {
      setState(() {
        _isMarkingNoShow = false;
        _apt = _apt.copyWith(status: AppointmentStatus.noShow);
      });
    }
  }

  // ─── Helpers ───────────────────────────────────────────────────────────────

  Widget _sectionLabel(String label) => Text(
    label,
    style: AppTypography.overline.copyWith(color: AppColors.ash),
  );

  Widget _buildDivider() => Padding(
    padding: const EdgeInsets.symmetric(vertical: AppSpacing.md),
    child: Container(height: 1, color: AppColors.smoke),
  );

  Widget _infoChip(IconData icon, String label) => Container(
    padding: const EdgeInsets.symmetric(
      horizontal: AppSpacing.sm,
      vertical: AppSpacing.xs,
    ),
    decoration: BoxDecoration(
      color: AppColors.slate,
      borderRadius: AppRadius.rSm,
      border: Border.all(color: AppColors.smoke),
    ),
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 11, color: AppColors.ash),
        const SizedBox(width: 4),
        Text(
          label,
          style: AppTypography.labelSm.copyWith(color: AppColors.pearl, fontSize: 11),
        ),
      ],
    ),
  );

  Widget _dataRow(String key, String value) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text(key, style: AppTypography.overline.copyWith(fontSize: 9)),
      const SizedBox(height: 2),
      Text(
        value,
        style: AppTypography.monoSm.copyWith(
          color: AppColors.pearl,
          fontSize: 12,
        ),
      ),
    ],
  );

  String _formatDate(DateTime dt) {
    const months = [
      'ene', 'feb', 'mar', 'abr', 'may', 'jun',
      'jul', 'ago', 'sep', 'oct', 'nov', 'dic',
    ];
    return '${dt.day} ${months[dt.month - 1]}';
  }

  AppBadgeVariant _statusToBadge(AppointmentStatus status) =>
      switch (status) {
        AppointmentStatus.confirmed => AppBadgeVariant.confirmed,
        AppointmentStatus.pendingConfirmation => AppBadgeVariant.pending,
        AppointmentStatus.cancelled => AppBadgeVariant.cancelled,
        AppointmentStatus.noShow => AppBadgeVariant.noShow,
        AppointmentStatus.completed => AppBadgeVariant.confirmed,
      };
}
