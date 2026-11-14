import 'package:flutter_test/flutter_test.dart';
import 'package:barberia_app/features/home_owner/domain/entities/dashboard_entities.dart';

// ─── Fixtures ────────────────────────────────────────────────────────────────

Appointment _makeAppointment({
  String id = 'apt-1',
  AppointmentStatus status = AppointmentStatus.confirmed,
  String basePrice = '150.00',
  String depositStatus = 'PAID',
  int rescheduleCount = 0,
  DateTime? startTime,
}) {
  final now = startTime ?? DateTime(2024, 1, 20, 10, 0);
  return Appointment(
    id: id,
    confirmationCode: 'ABC123',
    client: const AppointmentClient(id: 'c1', name: 'Juan Pérez', phone: '5551111111'),
    service: AppointmentService(
      id: 's1', name: 'Corte clásico', category: 'CORTE',
      durationMinutes: 30, basePrice: basePrice, depositAmount: '50.00',
    ),
    barberId: 'b1',
    barberName: 'Pedro García',
    startTime: now,
    endTime: now.add(const Duration(minutes: 30)),
    status: status,
    depositStatus: depositStatus,
    rescheduleCount: rescheduleCount,
    createdAt: now.subtract(const Duration(days: 1)),
  );
}

void main() {
  group('Appointment', () {

    group('computed properties', () {
      test('startTimeFormatted retorna HH:MM', () {
        final apt = _makeAppointment(startTime: DateTime(2024, 1, 20, 9, 30));
        expect(apt.startTimeFormatted, '09:30');
      });

      test('timeRange retorna rango correcto', () {
        final apt = _makeAppointment(startTime: DateTime(2024, 1, 20, 14, 0));
        expect(apt.timeRange, '14:00 – 14:30');
      });

      test('isPending true para pendingConfirmation', () {
        final apt = _makeAppointment(status: AppointmentStatus.pendingConfirmation);
        expect(apt.isPending, isTrue);
        expect(apt.isConfirmed, isFalse);
      });

      test('isConfirmed true para confirmed', () {
        final apt = _makeAppointment(status: AppointmentStatus.confirmed);
        expect(apt.isConfirmed, isTrue);
        expect(apt.isPending, isFalse);
      });

      test('isActive true para confirmed y pendingConfirmation', () {
        expect(_makeAppointment(status: AppointmentStatus.confirmed).isActive, isTrue);
        expect(_makeAppointment(status: AppointmentStatus.pendingConfirmation).isActive, isTrue);
        expect(_makeAppointment(status: AppointmentStatus.completed).isActive, isFalse);
        expect(_makeAppointment(status: AppointmentStatus.cancelled).isActive, isFalse);
        expect(_makeAppointment(status: AppointmentStatus.noShow).isActive, isFalse);
      });

      test('requiresDeposit true cuando PENDING', () {
        final apt = _makeAppointment(depositStatus: 'PENDING');
        expect(apt.requiresDeposit, isTrue);
      });

      test('requiresDeposit false cuando PAID', () {
        final apt = _makeAppointment(depositStatus: 'PAID');
        expect(apt.requiresDeposit, isFalse);
      });
    });

    group('copyWith — Freezed', () {
      test('copyWith status cambia status sin mutar el original', () {
        final apt = _makeAppointment(status: AppointmentStatus.confirmed);
        final noShow = apt.copyWith(status: AppointmentStatus.noShow);
        expect(noShow.status, AppointmentStatus.noShow);
        expect(apt.status, AppointmentStatus.confirmed); // Inmutable
      });
    });
  });

  group('AppointmentStatus', () {
    test('fromString parsea correctamente', () {
      expect(AppointmentStatusX.fromString('CONFIRMED'), AppointmentStatus.confirmed);
      expect(AppointmentStatusX.fromString('PENDING_CONFIRMATION'), AppointmentStatus.pendingConfirmation);
      expect(AppointmentStatusX.fromString('COMPLETED'), AppointmentStatus.completed);
      expect(AppointmentStatusX.fromString('CANCELLED'), AppointmentStatus.cancelled);
      expect(AppointmentStatusX.fromString('NO_SHOW'), AppointmentStatus.noShow);
    });

    test('fromString es case-insensitive', () {
      expect(AppointmentStatusX.fromString('confirmed'), AppointmentStatus.confirmed);
      expect(AppointmentStatusX.fromString('Confirmed'), AppointmentStatus.confirmed);
    });

    test('fromString retorna pendingConfirmation para valores desconocidos', () {
      expect(AppointmentStatusX.fromString('UNKNOWN'), AppointmentStatus.pendingConfirmation);
    });

    test('label retorna texto legible', () {
      expect(AppointmentStatus.confirmed.label, 'Confirmada');
      expect(AppointmentStatus.cancelled.label, 'Cancelada');
      expect(AppointmentStatus.noShow.label, 'No show');
    });
  });

  group('DashboardMetrics', () {
    test('empty() retorna métricas en cero', () {
      final m = DashboardMetrics.empty();
      expect(m.appointmentsToday, 0);
      expect(m.revenueWeek, '0.00');
      expect(m.attendanceRate, 0.0);
    });

    test('attendancePercent formatea correctamente', () {
      const m = DashboardMetrics(
        appointmentsToday: 5,
        appointmentsWeek: 5,
        revenueWeek: '750.00',
        attendanceRate: 0.8,
        pendingConfirmations: 1,
        completedToday: 4,
      );
      expect(m.attendancePercent, '80%');
    });

    test('attendancePercent 100%', () {
      const m = DashboardMetrics(
        appointmentsToday: 3,
        appointmentsWeek: 3,
        revenueWeek: '450.00',
        attendanceRate: 1.0,
        pendingConfirmations: 0,
        completedToday: 3,
      );
      expect(m.attendancePercent, '100%');
    });
  });
}
