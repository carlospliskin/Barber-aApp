import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../features/auth/presentation/notifiers/auth_notifier.dart';
import '../../domain/entities/dashboard_entities.dart';
import '../notifiers/home_owner_notifier.dart';
import '../widgets/appointment_detail_sheet.dart';
import '../widgets/dashboard_widgets.dart';
import '../widgets/home_owner_widgets.dart';

/// El barbero ve su propia agenda del día.
/// Reutiliza HomeOwnerNotifier — los datos ya están cargados.
/// La diferencia con el Owner:
///   - No tiene acceso a Servicios ni Config globales
///   - Solo ve las citas que le corresponden (filtrado por barberId)
///   - Puede cambiar a modo Cliente si tiene ese rol
class HomeBarberScreen extends ConsumerWidget {
  const HomeBarberScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user     = ref.watch(currentUserProvider);
    final state    = ref.watch(homeOwnerNotifierProvider);
    final notifier = ref.read(homeOwnerNotifierProvider.notifier);

    // Filtrar solo las citas del barbero autenticado
    final myAppointments = state.appointments.where((apt) =>
      apt.barberId == user?.userId,
    ).toList();

    // Métricas solo de sus citas
    final myMetrics = state.metrics != null
        ? notifier.calculateMetricsForBarber(myAppointments)
        : null;

    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.light.copyWith(
        statusBarColor: Colors.transparent),
      child: Scaffold(
        backgroundColor: AppColors.obsidian,
        body: RefreshIndicator(
          color: AppColors.gold,
          backgroundColor: AppColors.graphite,
          onRefresh: notifier.refresh,
          child: CustomScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            slivers: [

              // ── Header ──────────────────────────────────────────────
              SliverToBoxAdapter(
                child: SafeArea(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: AppSpacing.base, vertical: AppSpacing.md),
                    child: _BarberHeader(
                      userName: user?.displayName.split(' ').first ?? '',
                      barberiaName: user?.tenant?.name ?? '',
                      hasClientRole: user?.isClient ?? false,
                      onSwitchToClient: () => ref
                          .read(authNotifierProvider.notifier)
                          .switchMode('CLIENT'),
                      onLogout: () => _showLogout(context, ref),
                    ),
                  ),
                ),
              ),

              // ── Métricas propias ─────────────────────────────────────
              if (myMetrics != null)
                SliverToBoxAdapter(
                  child: Padding(
                    padding: AppSpacing.screenHorizontal,
                    child: MetricsStrip(metrics: myMetrics),
                  ),
                ),

              const SliverToBoxAdapter(child: SizedBox(height: AppSpacing.lg)),

              // ── Selector de fecha ─────────────────────────────────────
              SliverToBoxAdapter(
                child: Padding(
                  padding: AppSpacing.screenHorizontal,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('MI AGENDA', style: AppTypography.overline
                          .copyWith(color: AppColors.ash)),
                      const SizedBox(height: AppSpacing.sm),
                      DateSelector(
                        selectedDate: state.selectedDate,
                        onDateChanged: notifier.changeDate,
                      ),
                    ],
                  ),
                ),
              ),

              const SliverToBoxAdapter(child: SizedBox(height: AppSpacing.md)),

              // ── Lista de citas del barbero ────────────────────────────
              state.appointmentsLoading
                  ? _buildLoadingSliver()
                  : myAppointments.isEmpty
                      ? _buildEmptySliver()
                      : _buildAppointmentsSliver(context, ref, myAppointments),

              const SliverToBoxAdapter(child: SizedBox(height: 100)),
            ],
          ),
        ),
        bottomNavigationBar: _BarberBottomNav(
          hasClientRole: user?.isClient ?? false,
          onSwitchToClient: () =>
              ref.read(authNotifierProvider.notifier).switchMode('CLIENT'),
        ),
      ),
    );
  }

  Widget _buildLoadingSliver() {
    return SliverPadding(
      padding: AppSpacing.screenHorizontal,
      sliver: SliverList(
        delegate: SliverChildBuilderDelegate(
          (_, __) => _Skeleton(),
          childCount: 4,
        ),
      ),
    );
  }

  Widget _buildEmptySliver() {
    return SliverFillRemaining(
      child: EmptyDayWidget(onCreateAppointment: null),
    );
  }

  Widget _buildAppointmentsSliver(
    BuildContext context,
    WidgetRef ref,
    List<Appointment> appointments,
  ) {
    return SliverPadding(
      padding: AppSpacing.screenHorizontal,
      sliver: SliverList(
        delegate: SliverChildBuilderDelegate(
          (_, i) {
            final apt = appointments[i];
            return AppointmentCard(
              appointment: apt,
              onTap: () => showAppointmentDetail(context, ref, apt),
              onMarkNoShow: apt.isConfirmed
                  ? () => ref
                      .read(homeOwnerNotifierProvider.notifier)
                      .markNoShow(apt.id)
                  : null,
            );
          },
          childCount: appointments.length,
        ),
      ),
    );
  }

  Future<void> _showLogout(BuildContext context, WidgetRef ref) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.graphite,
        shape: RoundedRectangleBorder(borderRadius: AppRadius.rModal),
        title: Text('Cerrar sesión', style: AppTypography.headingMd),
        content: Text('¿Seguro que quieres salir?',
          style: AppTypography.bodyMd),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text('Cancelar', style: AppTypography.labelMd.copyWith(
              color: AppColors.silver)),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text('Salir', style: AppTypography.labelMd.copyWith(
              color: AppColors.error, fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );
    if (confirm == true && context.mounted) {
      ref.read(authNotifierProvider.notifier).logout();
    }
  }
}

// ─── Header del barbero ───────────────────────────────────────────────────────

class _BarberHeader extends StatelessWidget {
  final String userName;
  final String barberiaName;
  final bool hasClientRole;
  final VoidCallback onSwitchToClient;
  final VoidCallback onLogout;

  const _BarberHeader({
    required this.userName,
    required this.barberiaName,
    required this.hasClientRole,
    required this.onSwitchToClient,
    required this.onLogout,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(_greeting(), style: AppTypography.overline.copyWith(
                color: AppColors.ash)),
              const SizedBox(height: 2),
              Text(userName, style: AppTypography.displayMd.copyWith(
                fontSize: 22)),
              Text(barberiaName, style: AppTypography.monoSm.copyWith(
                color: AppColors.gold)),
            ],
          ),
        ),
        if (hasClientRole)
          GestureDetector(
            onTap: () {
              HapticFeedback.lightImpact();
              onSwitchToClient();
            },
            child: Container(
              padding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.sm + 2, vertical: AppSpacing.sm),
              decoration: BoxDecoration(
                color: AppColors.graphite,
                borderRadius: AppRadius.rPill,
                border: Border.all(color: AppColors.smoke)),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.person_outline_rounded,
                    size: 13, color: AppColors.silver),
                  const SizedBox(width: 4),
                  Text('Cliente', style: AppTypography.labelSm.copyWith(
                    color: AppColors.silver)),
                ],
              ),
            ),
          ),
        const SizedBox(width: AppSpacing.sm),
        GestureDetector(
          onTap: onLogout,
          child: Container(
            width: 36, height: 36,
            decoration: BoxDecoration(
              gradient: AppColors.goldGradient,
              shape: BoxShape.circle,
              border: Border.all(
                color: AppColors.gold.withOpacity(0.4), width: 1.5)),
            child: Center(
              child: Text(
                userName.isNotEmpty ? userName[0].toUpperCase() : 'B',
                style: AppTypography.labelLg.copyWith(
                  color: AppColors.obsidian, fontWeight: FontWeight.w900)),
            ),
          ),
        ),
      ],
    );
  }

  String _greeting() {
    final h = DateTime.now().hour;
    if (h < 12) return 'BUENOS DÍAS';
    if (h < 19) return 'BUENAS TARDES';
    return 'BUENAS NOCHES';
  }
}

// ─── BottomNav del barbero ────────────────────────────────────────────────────

class _BarberBottomNav extends StatelessWidget {
  final bool hasClientRole;
  final VoidCallback onSwitchToClient;

  const _BarberBottomNav({
    required this.hasClientRole,
    required this.onSwitchToClient,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 72,
      decoration: const BoxDecoration(
        color: AppColors.carbon,
        border: Border(top: BorderSide(color: AppColors.smoke))),
      child: SafeArea(
        top: false,
        child: Row(
          children: [
            // Tab activo — Agenda
            Expanded(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.calendar_today_rounded,
                    size: 22, color: AppColors.gold),
                  const SizedBox(height: 3),
                  Container(
                    width: 4, height: 4,
                    decoration: const BoxDecoration(
                      shape: BoxShape.circle, color: AppColors.gold)),
                ],
              ),
            ),
            // Botón modo cliente (si aplica)
            if (hasClientRole)
              Expanded(
                child: GestureDetector(
                  onTap: () {
                    HapticFeedback.mediumImpact();
                    onSwitchToClient();
                  },
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: AppSpacing.sm, vertical: 4),
                        decoration: BoxDecoration(
                          color: AppColors.goldSubtle,
                          borderRadius: AppRadius.rPill,
                          border: Border.all(
                            color: AppColors.gold.withOpacity(0.3))),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Icon(Icons.person_outline_rounded,
                              size: 12, color: AppColors.gold),
                            const SizedBox(width: 3),
                            Text('Cliente', style: AppTypography.overline
                                .copyWith(color: AppColors.gold, fontSize: 9)),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _Skeleton extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: AppSpacing.sm),
      height: 70,
      decoration: BoxDecoration(
        color: AppColors.graphite, borderRadius: AppRadius.rCard),
    );
  }
}
